# Task 31 - Cache Performance

## 1. Objective

Measure the same Course detail endpoint, Course ID, database data, workload, machine and source commit in cache `OFF`, `MISS` and `HIT` modes. Conclusions use only measured latency and database SELECT evidence from the official Phase B2 runs.

## 2. Integration snapshot

| Item | Value |
|---|---|
| Branch | `feat/week4-cache-coverage-regression` |
| Integration measurement commit | `2aa11d2aff81dab06dc0804016caf4f9e4425b28` |
| Source working tree during all runs | Clean |
| Final product commit | `PENDING_FINAL_PRODUCT_COMMIT` |
| Java runtime | `22.0.2` |
| Maven target / CI Java | `21` |
| Spring Boot | `4.1.1` |
| Docker client/server | `28.4.0` / `28.4.0` |
| Runtime database | MySQL `8.4.11` via Docker Compose, host port `13306` |
| Runtime cache | Redis 7.4-alpine via Docker Compose |
| Production cache TTL | 10 minutes |

The current HEAD contains TV3's cache implementation. TV2 has not modified its production cache files.

## 3. Endpoint and payload identity

- Endpoint: `GET /api/courses/{id}`.
- Health endpoint: `GET /api/health`.
- Response type: `CourseResponse` with `id`, `categoryId`, `categoryName`, `title`, `description`, `price`, `level` and `status`.
- The script requires an existing Course ID and never creates, edits or deletes Course data.
- Each successful summary records `CoursePayloadSha256` from the raw preflight response body.
- OFF, MISS and HIT are comparable only when they use the same Course ID and have matching payload hashes.

## 4. Verified cache contract

Course detail read is implemented in:

`backend/src/main/java/com/ccnlthd/course_management/service/impl/CourseServiceImpl.java`

The method uses:

```java
@Cacheable(cacheNames = "courseDetails", key = "#id")
```

Therefore the Redis key contract used by the benchmark is `courseDetails::<id>`.

Cache configuration is in `CourseCacheConfig.java`:

- Redis value serialization uses `JacksonJsonRedisSerializer<CourseResponse>`.
- Null values are not cached.
- TTL comes from `spring.cache.redis.time-to-live`, whose runtime default is `10m`.
- The Redis cache manager is transaction-aware.

Invalidation is implemented by the custom `CourseDetailCacheInvalidator`, not `@CacheEvict`:

- Course update/delete evicts only that Course ID after a successful transaction commit.
- Category rename invalidates the entire `courseDetails` cache after commit because cached responses include `categoryName`.
- A rollback does not execute the registered invalidation.

## 5. Workload and statistics

- Warm-up: `20` sequential requests.
- Measured requests: `100` sequential requests.
- Concurrency: `1`.
- Timing starts immediately before HTTP send and stops after the complete response body is read.
- Statistics: Average, Median, P95 nearest-rank, Min and Max.
- Primary comparison: Median, P95 and MySQL `Com_select` delta.
- `SPRING_JPA_SHOW_SQL=false` was supplied for every latency run, so Hibernate's `show-sql` property was disabled. The host environment also contained `DEBUG=release`, which enabled Spring Boot debug logging and still emitted Hibernate SQL DEBUG statements. This is a limitation of this integration measurement and must be removed or explicitly controlled for the final develop measurement.

## 6. Working-tree metadata

Successful summaries contain two repository states captured before the current run creates its output directory:

- `RawWorkingTree`: complete `git status --porcelain`, including prior benchmark result folders.
- `SourceWorkingTree`: the same status with only `scripts/cache-performance/results/**` excluded through Git pathspec.

Production source, tests, scripts outside the result folder, reports and configuration are never excluded. Official measurement requires `SourceWorkingTree = clean`; `RawWorkingTree` may become dirty after the first run because benchmark evidence is intentionally retained.

## 7. Mode preparation

### OFF

Start the backend with `SPRING_CACHE_TYPE=none`. OFF never accesses Redis. It performs health/Course preflight, warm-up, reads the DB counter, measures requests and reads the counter again.

### MISS

Before the workload, the script performs this unmeasured contract check:

```text
DEL key -> GET Course -> EXISTS == 1 -> TTL > 0 -> DEL key
```

If the key is missing or has no positive TTL, the run fails fast, writes `failure.json` and creates no successful summary. During warm-up and measurement it deletes only `courseDetails::<id>` before each request, outside the HTTP stopwatch.

### HIT

The script deletes the selected key once, primes it with one Course request, verifies existence and positive TTL, then runs warm-up and measured requests without deleting the key. It verifies the key and TTL again after measurement.

The script never calls `FLUSHALL` or `FLUSHDB`.

## 8. Database query evidence

The script reads:

```sql
SHOW GLOBAL STATUS LIKE 'Com_select';
```

`DbSelectDelta = SelectAfter - SelectBefore`. MySQL credentials are consumed inside the running Compose container and are not copied into logs or summaries.

`Com_select` is a global MySQL counter, so unrelated database traffic can affect the delta. Official measurements must run without other database traffic.

## 9. Phase B2 results

Course ID `1` was verified through the live API before measurement. All three summaries report the same endpoint, workload, machine metadata, integration commit and payload SHA-256.

| Metric | OFF | MISS | HIT |
|---|---:|---:|---:|
| Average (ms) | 27.275 | 35.962 | 9.854 |
| Median (ms) | 22.540 | 29.204 | 8.883 |
| P95 nearest-rank (ms) | 41.520 | 86.971 | 15.909 |
| Min (ms) | 15.318 | 12.392 | 6.554 |
| Max (ms) | 131.089 | 138.636 | 34.845 |
| DB SELECT delta | 201 | 201 | 1 |
| Cache TTL evidence | Not accessed | MISS contract: 600s | Prime: 600s; after run: 597s |
| Source working tree | Clean | Clean | Clean |

Payload SHA-256 for OFF, MISS and HIT:

```text
f66005bb73b459e336348f33aa2368d853992d17d396aeb05af6ab121cef1f64
```

Measured improvements:

- Median HIT improvement versus OFF: `60.590%`.
- Median HIT improvement versus MISS: `69.583%`.
- DB SELECT reduction for HIT versus OFF and MISS: `99.502%`.

The HIT median and P95 are lower than both OFF and MISS. Therefore this integration-branch measurement supports the conclusion that cache hits reduced Course-detail latency for this sequential workload. It does not establish final product performance.

## 10. Result evidence

- OFF: `scripts/cache-performance/results/2026-10-04_165922_off/`.
- MISS: `scripts/cache-performance/results/2026-10-04_170219_miss/`.
- HIT: `scripts/cache-performance/results/2026-10-04_170354_hit/`.
- Comparison: `scripts/cache-performance/results/comparison-2026-10-04_170517.md`.
- Each run directory contains `requests.csv`, `summary.json` and `summary.md`.

The historical failed attempts remain retained for traceability and are not included in the performance comparison. Each has `CompletedMeasuredRequests = 0`:

- `2026-10-04_141627_off`: Docker daemon unavailable during Phase A.
- `2026-10-04_165353_off`: native Java version output was treated as a terminating stderr record.
- `2026-10-04_165532_off`: native MySQL warning output was treated as a terminating stderr record.
- `2026-10-04_165856_off`: the process-local command adapter used for the rerun referenced the wrong scope.

The successful measurements used a process-local PowerShell adapter to normalize native stderr handling and execute the intended read-only `Com_select` query. The repository benchmark script and runtime configuration were not edited between OFF, MISS and HIT.

## 11. Integration versus final product evidence

- Benchmark CLI: `scripts/cache-performance/benchmark.ps1`.
- Usage and formulas: `scripts/cache-performance/README.md`.
- Cache configuration: `backend/src/main/java/com/ccnlthd/course_management/config/CourseCacheConfig.java`.
- Cache invalidator: `backend/src/main/java/com/ccnlthd/course_management/service/CourseDetailCacheInvalidator.java`.
- Course service: `backend/src/main/java/com/ccnlthd/course_management/service/impl/CourseServiceImpl.java`.
- Category service: `backend/src/main/java/com/ccnlthd/course_management/service/impl/CategoryServiceImpl.java`.
- Runtime configuration: `backend/src/main/resources/application.yml` and `docker-compose.yml`.

Status: `INTEGRATION_MEASUREMENT_COMPLETE` at commit `2aa11d2aff81dab06dc0804016caf4f9e4425b28`.

After the real merge into `develop`, rerun regression and OFF/MISS/HIT from the final develop commit. Only those later numbers may be presented as final Chapter 12 product performance evidence.
