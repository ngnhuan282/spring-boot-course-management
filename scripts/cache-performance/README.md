# Cache Performance Benchmark

This tool measures the same Course detail endpoint in cache `OFF`, `MISS`, and `HIT` modes. It does not create or modify Course data.

## Prerequisites

- Run commands from the repository root.
- Docker Desktop and the Compose services used by the selected mode must be running.
- The backend must be available at `http://localhost:8080` unless `-BaseUrl` is supplied.
- `-CourseId` must identify an existing Course.

Start MySQL:

```powershell
docker compose up -d mysql
```

For cache OFF, start the backend from `backend` with cache explicitly disabled:

```powershell
$env:SPRING_CACHE_TYPE="none"
$env:SPRING_JPA_SHOW_SQL="false"
.\mvnw.cmd spring-boot:run
```

For cache MISS/HIT, remove the OFF override and keep SQL console logging disabled:

```powershell
Remove-Item Env:SPRING_CACHE_TYPE -ErrorAction SilentlyContinue
$env:SPRING_JPA_SHOW_SQL="false"
.\mvnw.cmd spring-boot:run
```

Run the benchmark from the repository root:

```powershell
.\scripts\cache-performance\benchmark.ps1 -Mode off -CourseId 1
```

The example uses Course `1` because it exists in the repository's current seed data. The script never hardcodes or inserts this ID.

After the backend is restarted with cache enabled:

```powershell
.\scripts\cache-performance\benchmark.ps1 -Mode miss -CourseId 1
.\scripts\cache-performance\benchmark.ps1 -Mode hit -CourseId 1
```

## Parameters

| Parameter | Default | Purpose |
|---|---:|---|
| `-Mode` | required | `off`, `miss`, or `hit` |
| `-CourseId` | required | Existing Course ID |
| `-BaseUrl` | `http://localhost:8080` | Backend base URL |
| `-Warmup` | `20` | Unrecorded warm-up requests |
| `-Runs` | `100` | Recorded sequential requests |
| `-RedisService` | `redis` | Redis Compose service |
| `-CacheName` | `courseDetails` | Cache name used to build the Redis key |
| `-DbService` | `mysql` | MySQL Compose service |
| `-TtlMinutes` | `10` | Expected cache TTL metadata |
| `-ComposeFile` | repository `docker-compose.yml` | Compose file path |
| `-ResultsRoot` | `scripts/cache-performance/results` | Output directory |

## Workloads

- `OFF`: warm up, read DB counter, measure requests, read DB counter. Redis is never accessed.
- `MISS`: preflight by deleting the selected key, requesting the Course once, verifying that the key exists with a positive TTL, then deleting it again. During the workload, delete only `courseDetails::<id>` before each warm-up and measured request. Redis checks and key deletion are outside measured HTTP latency.
- `HIT`: delete the key once, prime it once, verify it exists and has a positive TTL, then run warm-up and measured requests without deleting it.

The tool never calls `FLUSHALL` or `FLUSHDB`.

## Metrics

Latency starts immediately before the HTTP send and stops only after the complete response body is read. Statistics are calculated from full-precision values:

- Average: arithmetic mean.
- Median: middle sample, or the mean of the two middle samples for an even count.
- P95: nearest-rank method, sorted position `ceil(0.95 * N)`.
- Min and Max.
- DB SELECT delta: MySQL `Com_select` after minus before.
- Data identity: SHA-256 of the raw Course preflight response body.

`Com_select` is a global MySQL counter. Unrelated database traffic during the measured workload can change the delta, so measurements should run without other traffic.

All latency runs disable Hibernate SQL console logging through `SPRING_JPA_SHOW_SQL=false` to reduce asymmetric console I/O between OFF/MISS and HIT. If SQL console output is needed for a report screenshot, run a separate verification that is not used as latency evidence.

Use JDK 21 for final product measurements when it is available. The benchmark records the actual Java runtime, so a run made with another JDK must be reported with that real version.

## Output

Each attempt creates a timestamped directory such as:

```text
scripts/cache-performance/results/2026-10-04_150000_off/
  requests.csv
  summary.json
  summary.md
```

A failed attempt writes `failure.json` and, when available, partial `requests.csv`. It does not write a successful summary.

The output records branch, commit, two working-tree states, Java, Spring Boot, OS, CPU, RAM, Docker, endpoint, workload, cache key, TTL observations, and DB counter values. `RawWorkingTree` reflects the complete Git status. `SourceWorkingTree` excludes only `scripts/cache-performance/results/**`, so evidence from an earlier run does not make unchanged source appear dirty. Production source, tests, scripts, reports, and configuration remain part of `SourceWorkingTree`. No database or Redis password is written to the result files.
