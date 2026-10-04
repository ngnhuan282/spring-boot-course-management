# Task 31 - Cache Performance

## 1. Objective

Measure the same Course detail endpoint, Course ID, database data, workload, machine and source commit in cache `OFF`, `MISS` and `HIT` modes. Conclusions will use only measured latency and database SELECT evidence from the official Phase B2 runs.

Phase B1 prepares and validates the integrated source. It does not produce official OFF/MISS/HIT numbers.

## 2. Integration snapshot

| Item | Value |
|---|---|
| Branch | `feat/week4-cache-coverage-regression` |
| Base HEAD | `0470809a8db62694d390b179d46281e70d985df8` |
| Working tree | Dirty - uncommitted TV2 Phase B1 work |
| Final product commit | `PENDING_FINAL_PRODUCT_COMMIT` |
| Java runtime | `22.0.2` |
| Maven target / CI Java | `21` |
| Spring Boot | `4.1.1` |
| Docker daemon | Available during Phase B1 preflight |
| Runtime database | MySQL 8.4 via Docker Compose |
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
- Hibernate SQL console logging is disabled with `SPRING_JPA_SHOW_SQL=false` for every latency run.

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

## 9. Current result status

| Mode | Median | P95 | DB SELECT delta | Status |
|---|---:|---:|---:|---|
| OFF | Not measured | Not measured | Not measured | `PENDING_PHASE_B2` |
| MISS | Not measured | Not measured | Not measured | `PENDING_PHASE_B2` |
| HIT | Not measured | Not measured | Not measured | `PENDING_PHASE_B2` |

The Phase A OFF attempt at `2026-10-04T14:16:28+07:00` remains historical failure evidence at `scripts/cache-performance/results/2026-10-04_141627_off/failure.json`. Docker was unavailable then, no measured request completed, and it is not performance evidence.

No performance conclusion is made in Phase B1.

## 10. Evidence and next step

- Benchmark CLI: `scripts/cache-performance/benchmark.ps1`.
- Usage and formulas: `scripts/cache-performance/README.md`.
- Cache configuration: `backend/src/main/java/com/ccnlthd/course_management/config/CourseCacheConfig.java`.
- Cache invalidator: `backend/src/main/java/com/ccnlthd/course_management/service/CourseDetailCacheInvalidator.java`.
- Course service: `backend/src/main/java/com/ccnlthd/course_management/service/impl/CourseServiceImpl.java`.
- Category service: `backend/src/main/java/com/ccnlthd/course_management/service/impl/CategoryServiceImpl.java`.
- Runtime configuration: `backend/src/main/resources/application.yml` and `docker-compose.yml`.

After the user commits Phase B1, Phase B2 must run OFF/MISS/HIT from that stable commit, verify `SourceWorkingTree = clean` for every run and compare the three payload hashes before reporting results.
