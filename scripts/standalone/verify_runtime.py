"""Real Docker/API/MySQL/Redis checks used by the standalone CI/CD jobs."""
import argparse
import csv
import datetime as dt
import decimal
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import urllib.error
import urllib.request


def now():
    return dt.datetime.now(dt.timezone.utc).isoformat()


class Proof:
    def __init__(self, args):
        self.args = args
        self.root = Path(args.output).resolve()
        self.root.mkdir(parents=True, exist_ok=True)
        self.checks = []
        self.compose = ['docker', 'compose', '-p', args.project, '-f', args.compose_file]

    def save(self, relative, value):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        text = value if isinstance(value, str) else json.dumps(value, indent=2, ensure_ascii=False)
        path.write_text(text + '\n', encoding='utf-8')

    def command(self, name, argv, timeout=150):
        result = subprocess.run(argv, capture_output=True, text=True, timeout=timeout, check=False)
        self.save('raw/' + name + '.json', {'time': now(), 'command': argv,
                  'exit_code': result.returncode, 'stdout': result.stdout, 'stderr': result.stderr})
        if result.returncode:
            raise RuntimeError(f'{name} failed ({result.returncode}): {result.stderr[-1500:]}')
        return result.stdout.strip()

    def check(self, name, condition, observed=None):
        self.checks.append({'name': name, 'passed': bool(condition), 'observed': observed})
        if not condition:
            raise AssertionError(f'{name}: {observed!r}')
        print('PASS:', name, flush=True)

    def mysql(self, name, sql):
        shell = 'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysql -h127.0.0.1 -u"$MYSQL_USER" -D"$MYSQL_DATABASE" --batch --raw --skip-column-names --execute "$1"'
        return self.command(name, self.compose + ['exec', '-T', 'mysql', 'sh', '-c', shell, 'sh', sql])

    def history(self, name):
        text = self.mysql(name, "SELECT JSON_OBJECT('rank',installed_rank,'version',version,'script',script,'checksum',checksum,'success',success,'installed_on',installed_on) FROM flyway_schema_history ORDER BY installed_rank")
        rows = [json.loads(line) for line in text.splitlines() if line.strip()]
        self.save(name + '.json', rows)
        return rows

    def rows(self, name):
        text = self.mysql(name, "SELECT JSON_OBJECT('id',id,'title',title,'description',description,'price',price,'level',level,'status',status,'created_at',created_at,'updated_at',updated_at) FROM courses ORDER BY id")
        rows = [json.loads(line) for line in text.splitlines() if line.strip()]
        self.save(name + '.json', rows)
        return rows

    def api(self, name, method, path, body=None):
        url = self.args.base_url.rstrip('/') + path
        encoded = None if body is None else json.dumps(body).encode('utf-8')
        request = urllib.request.Request(url, data=encoded, method=method,
                                         headers={'Content-Type': 'application/json'})
        try:
            response = urllib.request.urlopen(request, timeout=40)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            status = response.code
            raw = response.read().decode('utf-8')
            headers = dict(response.headers)
        self.save('http/' + name + '.json', {'time': now(), 'method': method, 'url': url,
                  'request': body, 'status': status, 'headers': headers, 'response': raw})
        return status, json.loads(raw) if raw else None

    def preflight(self):
        if (self.root / 'preflight.json').exists():
            raise RuntimeError('Use a new proof output directory for each launch')
        config = json.loads(self.command('compose-config', self.compose + ['config', '--format', 'json']))
        self.check('exactly_three_services', set(config['services']) == {'mysql', 'redis', 'backend'})
        volume = config['volumes']['standalone_mysql_data']
        self.check('volume_is_project_scoped', not volume.get('external') and volume['name'] == self.args.project + '_standalone_mysql_data', volume)
        containers = self.command('containers-before', ['docker', 'ps', '-aq', '--filter', 'label=com.docker.compose.project=' + self.args.project])
        volumes = self.command('volumes-before', ['docker', 'volume', 'ls', '-q', '--filter', 'name=^' + re.escape(volume['name']) + '$'])
        self.check('no_preexisting_project_containers', not containers, containers)
        self.check('no_preexisting_mysql_volume', not volumes, volumes)
        sha = self.command('source-revision', ['git', 'rev-parse', 'HEAD'])
        self.check('checkout_matches_expected_commit', sha == self.args.expected_revision, sha)
        self.save('preflight.json', {'status': 'PASS', 'source_sha': sha, 'project': self.args.project,
                  'volume_name': volume['name'], 'time': now(), 'checks': self.checks})

    def verify(self):
        pre = json.loads((self.root / 'preflight.json').read_text(encoding='utf-8'))
        self.check('fresh_project_preflight_recorded', pre['status'] == 'PASS' and pre['source_sha'] == self.args.expected_revision and pre['project'] == self.args.project)
        identities = {}
        inspections = {}
        for service in ('mysql', 'redis', 'backend'):
            identity = self.command(service + '-id', self.compose + ['ps', '-q', service])
            self.check(service + '_has_one_container', bool(identity) and '\n' not in identity, identity)
            info = json.loads(self.command(service + '-inspect', ['docker', 'inspect', identity]))[0]
            identities[service] = identity
            inspections[service] = info
            self.check(service + '_healthy', info['State']['Running'] and info['State'].get('Health', {}).get('Status') == 'healthy', info['State'])
        backend = inspections['backend']
        image = json.loads(self.command('backend-image', ['docker', 'image', 'inspect', backend['Image']]))[0]
        self.check('image_revision_matches_commit', image['Config']['Labels'].get('org.opencontainers.image.revision') == self.args.expected_revision, image['Config']['Labels'])
        self.check('backend_runs_as_non_root', image['Config'].get('User') not in ('', '0', 'root', None), image['Config'].get('User'))
        mounts = [m.get('Name') for m in inspections['mysql']['Mounts'] if m['Destination'] == '/var/lib/mysql']
        self.check('mysql_uses_fresh_named_volume', mounts == [pre['volume_name']], mounts)
        if self.args.delivery_image:
            self.check('published_image_uses_digest', bool(re.fullmatch(r'ghcr\.io/[a-z0-9_./-]+@sha256:[0-9a-f]{64}', self.args.delivery_image)))
            self.check('running_image_is_published_digest', backend['Config']['Image'] == self.args.delivery_image and self.args.delivery_image in image.get('RepoDigests', []), image.get('RepoDigests'))

        tables = self.mysql('business-tables', "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME").splitlines()
        self.check('only_course_and_flyway_tables_exist', tables == ['courses', 'flyway_schema_history'], tables)
        category = int(self.mysql('category-column', "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='courses' AND COLUMN_NAME='category_id'"))
        self.check('course_has_no_category_column', category == 0, category)
        history = self.history('flyway-before-restart')
        self.check('flyway_v1_v2_success', len(history) == 2 and [r['version'] for r in history] == ['1', '2'] and all(r['success'] == 1 and isinstance(r['checksum'], int) for r in history), history)
        seed = self.rows('seed-rows')
        self.check('three_seed_courses', len(seed) == 3 and [r['id'] for r in seed] == [1, 2, 3], seed)
        status, health = self.api('health', 'GET', '/api/health')
        self.check('health_api_200', status == 200 and health.get('status') == 'UP', health)
        status, listed = self.api('seed-list', 'GET', '/api/courses')
        self.check('seed_api_returns_three_courses', status == 200 and isinstance(listed, list) and len(listed) == 3)
        title = 'C8 standalone ' + self.args.project
        payload = {'title': title, 'description': 'Course-only Docker and Flyway proof', 'price': 129000,
                   'level': 'BEGINNER', 'status': 'PUBLISHED'}
        status, created = self.api('create-course', 'POST', '/api/courses', payload)
        self.check('create_without_category_returns_201', status == 201 and created.get('title') == title and isinstance(created.get('id'), int), created)
        identity = created['id']
        status, received = self.api('get-course', 'GET', f'/api/courses/{identity}')
        self.check('get_created_course_returns_same_data', status == 200 and received['id'] == identity and received['title'] == title and decimal.Decimal(str(received['price'])) == decimal.Decimal('129000'))
        key = f'standaloneCourseDetails::{identity}'
        ttl = int(self.command('cache-ttl', self.compose + ['exec', '-T', 'redis', 'redis-cli', 'TTL', key]))
        cached = self.command('cache-json', self.compose + ['exec', '-T', 'redis', 'redis-cli', 'GET', key])
        self.check('redis_cache_and_ttl_match_course', 0 < ttl <= 600 and json.loads(cached)['title'] == title, {'key': key, 'ttl': ttl})
        status, invalid = self.api('invalid-course', 'POST', '/api/courses', dict(payload, title='', price=-1))
        self.check('invalid_request_returns_400', status == 400 and invalid.get('code') == 'VALIDATION_FAILED', invalid)
        payload['title'] = title + ' updated'
        status, updated = self.api('update-course', 'PUT', f'/api/courses/{identity}', payload)
        self.check('update_returns_200', status == 200 and updated['title'] == payload['title'])
        exists = int(self.command('cache-after-update', self.compose + ['exec', '-T', 'redis', 'redis-cli', 'EXISTS', key]))
        self.check('committed_update_evicts_cache', exists == 0, exists)
        status, refreshed = self.api('get-updated-course', 'GET', f'/api/courses/{identity}')
        self.check('updated_course_is_readable', status == 200 and refreshed['title'] == payload['title'])
        status, temporary = self.api('temporary-course', 'POST', '/api/courses', dict(payload, title=title + ' delete'))
        self.check('temporary_course_created', status == 201)
        status, _ = self.api('delete-course', 'DELETE', f"/api/courses/{temporary['id']}")
        self.check('delete_returns_204', status == 204)
        status, missing = self.api('deleted-course', 'GET', f"/api/courses/{temporary['id']}")
        self.check('deleted_course_returns_404', status == 404 and missing.get('code') == 'COURSE_NOT_FOUND', missing)
        before = self.rows('rows-before-restart')
        self.check('mysql_row_matches_api', len(before) == 4 and any(r['id'] == identity and r['title'] == payload['title'] for r in before), before)
        self.command('restart-backend', self.compose + ['restart', 'backend'])
        self.command('wait-after-restart', self.compose + ['up', '--no-build', '-d', '--wait', '--wait-timeout', '180', 'backend'], timeout=220)
        after_history = self.history('flyway-after-restart')
        after = self.rows('rows-after-restart')
        self.check('flyway_history_and_checksums_unchanged', history == after_history)
        self.check('all_course_rows_unchanged_after_restart', before == after)
        after_id = self.command('backend-id-after-restart', self.compose + ['ps', '-q', 'backend'])
        self.check('same_backend_container_restarted', after_id == identities['backend'])
        status, retained = self.api('get-course-after-restart', 'GET', f'/api/courses/{identity}')
        self.check('course_readable_after_restart', status == 200 and retained['title'] == payload['title'])
        self.save('runtime-result.json', {'status': 'PASS', 'source_sha': self.args.expected_revision,
                  'project': self.args.project, 'finished_at': now(), 'checks': self.checks,
                  'image_id': backend['Image'], 'mysql_volume': pre['volume_name'], 'course_id': identity,
                  'delivery_image': self.args.delivery_image})
        if self.args.delivery_image:
            repository, digest = self.args.delivery_image.split('@', 1)
            self.save('delivery-metadata.json', {'status': 'PUBLISHED_AND_RUNTIME_VERIFIED',
                      'source_sha': self.args.expected_revision, 'image_repository': repository,
                      'image_digest': digest, 'image_ref': self.args.delivery_image,
                      'tag': repository + ':sha-' + self.args.expected_revision,
                      'workflow_run_id': os.environ['GITHUB_RUN_ID'],
                      'run_attempt': os.environ['GITHUB_RUN_ATTEMPT'], 'finished_at': now(),
                      'scope': 'GHCR delivery and ephemeral GitHub runner deployment; independent Course experiment'})

    def seal(self):
        rows = [{'path': p.relative_to(self.root).as_posix(), 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()}
                for p in sorted(self.root.rglob('*')) if p.is_file() and p.name != 'sha256.csv']
        with (self.root / 'sha256.csv').open('w', newline='', encoding='utf-8') as stream:
            writer = csv.DictWriter(stream, fieldnames=['path', 'sha256'])
            writer.writeheader()
            writer.writerows(rows)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--phase', choices=['preflight', 'verify', 'seal'], required=True)
    parser.add_argument('--compose-file', default='docker-compose.standalone.yml')
    parser.add_argument('--project', required=True)
    parser.add_argument('--base-url', default='http://127.0.0.1:8091')
    parser.add_argument('--expected-revision', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--delivery-image', default='')
    args = parser.parse_args()
    if not re.fullmatch(r'[a-z0-9][a-z0-9_-]{1,80}', args.project) or not re.fullmatch(r'[0-9a-f]{40}', args.expected_revision):
        parser.error('Invalid project or source commit')
    proof = Proof(args)
    try:
        getattr(proof, args.phase)()
    except Exception as error:
        proof.save('failure-' + args.phase + '.json', {'status': 'FAIL', 'source_sha': args.expected_revision,
                   'time': now(), 'error': str(error), 'checks': proof.checks})
        print(str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
