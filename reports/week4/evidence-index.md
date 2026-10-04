# Week 4 Evidence Index

## Repository state

- Branch: `feat/week4-cache-coverage-regression`.
- Base HEAD: `0470809a8db62694d390b179d46281e70d985df8`.
- Upstream: `origin/feat/week4-cache-coverage-regression`.
- Working tree: Dirty - Phase B1 changes have not been committed.
- Regression evidence time: `2026-10-04T16:23:13+07:00`.
- Cache status: `Integrated - benchmark pending Phase B2`.
- Final product commit: `PENDING_FINAL_PRODUCT_COMMIT`.
- Commit/push/merge performed by Codex: no.

## Task 31 - Cache performance

- Benchmark script: `scripts/cache-performance/benchmark.ps1`.
- Benchmark README: `scripts/cache-performance/README.md`.
- Product cache contract: `GET /api/courses/{id}` -> `courseDetails::<id>`, production TTL `10m`.
- Cache implementation and six integration tests are present and verified.
- Multi-run metadata: `RawWorkingTree` plus `SourceWorkingTree`, which excludes only benchmark result folders.
- DB query measurement: MySQL global `Com_select` before/after delta.
- OFF result: `PENDING_PHASE_B2`.
- MISS result: `PENDING_PHASE_B2`.
- HIT result: `PENDING_PHASE_B2`.
- Successful `requests.csv`, `summary.json` and `summary.md`: not created because official measurement must wait for the Phase B1 commit.
- Report: `reports/week4/task31-cache-performance.md`.
- Status: `PENDING_PHASE_B2`.

Historical Phase A evidence is retained and is not treated as a successful benchmark:

- Failed OFF attempt: `scripts/cache-performance/results/2026-10-04_141627_off/failure.json`.
- Cause: Docker daemon unavailable during that attempt.
- Completed measured requests: `0`.
- No latency or DB SELECT result is inferred from the failed attempt.

## Task 35 - Technical coverage

- Coverage report: `reports/week4/task35-technical-coverage.md`.
- Base product commit: `0470809a8db62694d390b179d46281e70d985df8`.
- T1 evidence: controllers, services, entities, repositories and JPA relations.
- Optional evidence: Bean Validation and standardized exception handling.
- T2 evidence: Git workflow, testing, CI configuration, Swagger, README and Docker Compose.
- T3 cache evidence: Course detail Redis cache, JSON serialization, TTL and after-commit invalidation.
- Cache row: `Integrated - benchmark pending`.
- CI status: workflow configured; successful GitHub run still needs external evidence.
- Final develop/product commit refresh: required after the real merge.

## Task 36 - Regression

- Command: `.\mvnw.cmd clean test` from `backend`.
- Official Phase B1 log: `reports/week4/raw/regression-integration-2026-10-04_162137.log` (UTF-8).
- Test suites: `11`.
- Total tests: `71`.
- Passed: `71`.
- Failures: `0`.
- Errors: `0`.
- Skipped: `0`.
- Cache integration: `6/6` passed with Testcontainers Redis 7.4-alpine.
- Automated datasource: H2 in-memory with MySQL compatibility mode.
- Runtime Compose environment: MySQL 8.4 plus Redis 7.4-alpine, pending Phase B2 verification.
- Regression report: `reports/week4/task36-regression.md`.
- Bug log: `reports/week4/bug-log.md`.
- Status: integrated regression `PASS`; runtime demo and official benchmark `PENDING_PHASE_B2`.

Historical Phase A regression evidence remains available:

- `reports/week4/raw/regression-2026-10-04_150246.log` - previous 65-test passing run.
- `reports/week4/raw/regression-2026-10-04_141505.log` - earlier 65-test passing run.
- `reports/week4/raw/regression-environment-blocked-2026-10-04_141429.log` - preliminary environment failure.

These files are retained for traceability and are not presented as the Phase B1 integrated result.

## AI disclosure evidence

- AI issue log: `reports/week4/ai-issue-log.md`.
- Real issues recorded: `2`.
- Issue 1: the first MISS benchmark draft did not verify that the expected Redis key and positive TTL were created.
- Issue 2: benchmark output was initially created before Git metadata capture and could make a clean tree appear dirty.
- No additional AI issue was invented.

## Evidence needed for report screenshots

### Suggested Figure 12.7 - Integrated regression

Capture the terminal showing:

- branch and commit output;
- `Tests run: 71, Failures: 0, Errors: 0, Skipped: 0`;
- `BUILD SUCCESS`;
- the six passing `CourseCacheIntegrationTests`.

### Suggested Figure 12.8 - Cache OFF/MISS/HIT

Status: `PENDING_PHASE_B2`.

After the Phase B1 commit, capture the same endpoint and Course ID with:

- matching `CoursePayloadSha256` for OFF, MISS and HIT;
- `SourceWorkingTree = clean` for all three runs;
- Redis key `courseDetails::<id>` for MISS/HIT;
- latency and `Com_select` comparison from the three summaries.

### Suggested Figure 12.9 - Cache invalidation

Automated integration status: `PASS`.

Runtime screenshot remains Phase B2 evidence. Capture:

- cached Course value before update;
- Course update/delete or Category rename;
- expected Redis key eviction/invalidation;
- next GET returning current data.

## Screenshot checklist

### Chapter 12

- [ ] Swagger UI displays Category, Course, Student and Enrollment APIs
- [ ] Enrollment creation succeeds with HTTP 201
- [ ] Enrollment validation fails with HTTP 400
- [ ] Duplicate Enrollment fails with HTTP 409
- [ ] MySQL Enrollment row shows `student_id` and `course_id`
- [ ] Maven/Surefire result shows 71 passing tests and the current commit
- [ ] Cache integration suite shows 6 passing tests
- [ ] GitHub Actions successful run, if available
- [ ] GitHub PR reviewer and merge evidence
- [ ] Redis key and official cache OFF/MISS/HIT evidence from Phase B2
- [ ] Runtime cache invalidation evidence from Phase B2

### Chapter 13

- [ ] Qualifying real product bug raw error/log, if one occurs
- [ ] Matching fixed result for a qualifying real product bug, if one occurs

## Exact next commands after the Phase B1 commit

At repository root, start the runtime services:

```powershell
docker compose up -d --wait mysql redis
docker compose ps
```

In a backend terminal, start OFF with cache and SQL console logging disabled:

```powershell
cd backend
$env:SPRING_CACHE_TYPE="none"
$env:SPRING_JPA_SHOW_SQL="false"
.\mvnw.cmd spring-boot:run
```

In another terminal at repository root:

```powershell
.\scripts\cache-performance\benchmark.ps1 -Mode off -CourseId 1 -Warmup 20 -Runs 100
```

Restart the backend from `backend` with cache enabled before MISS/HIT:

```powershell
cd backend
Remove-Item Env:SPRING_CACHE_TYPE -ErrorAction SilentlyContinue
$env:SPRING_JPA_SHOW_SQL="false"
.\mvnw.cmd spring-boot:run
```

Then run from repository root in another terminal:

```powershell
.\scripts\cache-performance\benchmark.ps1 -Mode miss -CourseId 1 -Warmup 20 -Runs 100
.\scripts\cache-performance\benchmark.ps1 -Mode hit -CourseId 1 -Warmup 20 -Runs 100
```
