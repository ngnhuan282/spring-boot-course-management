# Week 4 Evidence Index

## Repository state

- Branch: `feat/week4-cache-coverage-regression`.
- Integration measurement commit: `2aa11d2aff81dab06dc0804016caf4f9e4425b28`.
- Phase B2 evidence commit: `dc0e07f0460e90a493bd8acfe2cec89307c96a61`.
- Measurement source working tree: `clean` in OFF, MISS and HIT summaries.
- Current working tree: dirty because the post-Phase-B2 tooling cleanup is not committed.
- Cache status: integrated; integration benchmark complete.
- Final product commit: `PENDING_FINAL_PRODUCT_COMMIT`.
- Commit/push/merge performed by Codex: no.

Runtime metadata for the benchmark:

- Java `22.0.2` on this machine; Maven/CI target Java `21`.
- Spring Boot `4.1.1`.
- Docker client/server `28.4.0`.
- Docker Compose MySQL `8.4.11` at host port `13306`.
- Docker Compose Redis `7.4-alpine` at host port `6379`.
- `SPRING_JPA_SHOW_SQL=false` supplied to both runtime modes. Host `DEBUG=release` still caused Hibernate SQL DEBUG statements; this limitation is disclosed in Task 31 and must be controlled in the final develop rerun.

## Task 31 - Cache performance

- Report: `reports/week4/task31-cache-performance.md`.
- Benchmark script: `scripts/cache-performance/benchmark.ps1`.
- Benchmark README: `scripts/cache-performance/README.md`.
- Endpoint: `GET /api/courses/1`.
- Workload: 20 warm-up, 100 measured, concurrency 1.
- Payload SHA-256 in all modes: `f66005bb73b459e336348f33aa2368d853992d17d396aeb05af6ab121cef1f64`.
- Cache contract: `courseDetails::1`, JSON `CourseResponse`, production TTL 10 minutes.

| Mode | Evidence | Median | P95 | DB SELECT delta |
|---|---|---:|---:|---:|
| OFF | `scripts/cache-performance/results/2026-10-04_165922_off/` | 22.540ms | 41.520ms | 201 |
| MISS | `scripts/cache-performance/results/2026-10-04_170219_miss/` | 29.204ms | 86.971ms | 201 |
| HIT | `scripts/cache-performance/results/2026-10-04_170354_hit/` | 8.883ms | 15.909ms | 1 |

- Comparison: `scripts/cache-performance/results/comparison-2026-10-04_170517.md`.
- Median HIT improvement versus OFF: `60.590%`.
- Median HIT improvement versus MISS: `69.583%`.
- Status: `INTEGRATION_MEASUREMENT_COMPLETE`.

Each successful run directory contains `requests.csv`, `summary.json` and `summary.md`. The comparison passed commit, branch, endpoint, Course ID, workload, machine metadata, source-tree and payload-hash invariants.

Historical/failed benchmark evidence is retained and excluded from the comparison because every attempt completed zero measured requests:

- `scripts/cache-performance/results/2026-10-04_141627_off/failure.json`: Docker unavailable in Phase A.
- `scripts/cache-performance/results/2026-10-04_165353_off/failure.json`: native Java stderr handling.
- `scripts/cache-performance/results/2026-10-04_165532_off/failure.json`: native MySQL warning handling.
- `scripts/cache-performance/results/2026-10-04_165856_off/failure.json`: process-local adapter scope error.

The benchmark portability issue is recorded in `reports/week4/bug-log.md`; no failed attempt is presented as performance evidence.

Post-Phase-B2 tooling verification, excluded from performance conclusions:

- Direct OFF/MISS/HIT smoke: `scripts/cache-performance/results/tooling-smoke-2026-10-04_191900/` with Course ID `1`, warm-up `1`, runs `3` and concurrency `1`.
- Initial direct-smoke SQL quoting failure: `scripts/cache-performance/results/tooling-smoke-2026-10-04_191900/2026-10-04_191944_off/failure.json`.
- Non-zero native exit enforcement: `scripts/cache-performance/results/tooling-nonzero-2026-10-04_191833/2026-10-04_191833_off/failure.json`; child process exited `1`.
- Chapter 13.2 candidate status: `RESOLVED_POST_PHASE_B2`.

## Task 35 - Technical coverage

- Coverage report: `reports/week4/task35-technical-coverage.md`.
- T1: IoC/DI, Controller-Service layering and Spring Data JPA.
- Optional: Bean Validation and standardized exception handling.
- T2: Git workflow, automated testing, configured CI, Swagger/OpenAPI, README and Docker Compose.
- T3: Course detail Spring Cache/Redis, JSON serialization, TTL and after-commit invalidation.
- Cache verification: six Testcontainers Redis tests plus official integration OFF/MISS/HIT evidence.
- Cache row: `Integrated`.
- Overall status: `INTEGRATION_CANDIDATE_COMPLETE`.
- CI status: workflow configured; successful GitHub run still needs external URL/screenshot evidence.
- Final develop/product commit refresh: required after the real merge.

## Task 36 - Regression

- Report: `reports/week4/task36-regression.md`.
- Command: `.\mvnw.cmd clean test` from `backend`.
- Final raw UTF-8 log: `reports/week4/raw/regression-integration-final-2026-10-04_170645.log`.
- Test suites: `11`.
- Total/passed: `71/71`.
- Failures/errors/skipped: `0/0/0`.
- Cache integration: `6/6` passed with Testcontainers Redis 7.4-alpine.
- Automated datasource: H2 in-memory with MySQL compatibility mode.
- Runtime benchmark: Compose MySQL 8.4.11 plus Redis 7.4-alpine, production TTL 10 minutes.
- Course demo: `REPRODUCIBLE`; see `reports/week4/course-demo-reproducibility.md`.
- Bug log: `reports/week4/bug-log.md`.
- Status: `PASS` on the integration candidate; final develop rerun required.

Historical regression evidence remains available and is not presented as the final Phase B2 run:

- `reports/week4/raw/regression-integration-2026-10-04_162137.log`: Phase B1, 71 tests passed.
- `reports/week4/raw/regression-2026-10-04_150246.log`: Phase A, 65 tests passed.
- `reports/week4/raw/regression-2026-10-04_141505.log`: earlier Phase A, 65 tests passed.
- `reports/week4/raw/regression-environment-blocked-2026-10-04_141429.log`: preliminary environment failure.

## AI disclosure evidence

- AI issue log: `reports/week4/ai-issue-log.md`.
- Real AI issues recorded: exactly `2`.
- Issue 1: the first MISS benchmark draft did not verify expected Redis key creation and positive TTL.
- Issue 2: benchmark output was initially created before Git metadata capture and could make a clean tree appear dirty.
- No third AI issue was added.

## Screenshot checklist

The following screenshots still need to be captured by a team member; none is claimed as already available:

- [ ] Current commit plus Maven `BUILD SUCCESS` and `Tests run: 71, Failures: 0, Errors: 0, Skipped: 0`
- [ ] Six passing `CourseCacheIntegrationTests`
- [ ] Swagger UI displays Category, Course, Student and Enrollment APIs
- [ ] Enrollment creation succeeds with HTTP 201
- [ ] Enrollment validation fails with HTTP 400
- [ ] Duplicate Enrollment fails with HTTP 409
- [ ] MySQL Enrollment row shows `student_id` and `course_id`
- [ ] Redis key `courseDetails::1` and positive TTL
- [ ] OFF/MISS/HIT comparison with matching payload SHA-256 and commit
- [ ] Cache invalidation after Course update/delete or Category rename
- [ ] GitHub Actions successful run, if available
- [ ] GitHub PR reviewer and merge evidence
- [ ] Chapter 13.2 benchmark tooling bug before/after, direct smoke pass and non-zero failure evidence

## Required after the real develop merge

These integration-branch numbers are not product-final evidence. After merge:

```powershell
git switch develop
git pull
git rev-parse HEAD
git status
```

Then run a fresh full regression and repeat OFF, MISS and HIT with the same endpoint, Course ID, workload and payload-hash validation. Refresh Task 31, Task 35 and Task 36 with the final develop commit and use only those new numbers for the final Chapter 12 product claim.

Before starting either backend mode for that measurement:

```powershell
Remove-Item Env:DEBUG -ErrorAction SilentlyContinue
$env:SPRING_JPA_SHOW_SQL="false"
```

Verify that Hibernate SQL DEBUG output is absent. If it still appears, stop before measurement and correct the logging environment.
