# Task 36 - Regression Report

## Final integration regression run

- Branch: `feat/week4-cache-coverage-regression`.
- Integration measurement commit: `2aa11d2aff81dab06dc0804016caf4f9e4425b28`.
- Command: `.\mvnw.cmd clean test` from `backend`.
- Maven exit code: `0`.
- Finished: `2026-10-04T17:07:54+07:00`.
- Total time: `01:06 min`.
- Raw UTF-8 log: `reports/week4/raw/regression-integration-final-2026-10-04_170645.log`.
- Surefire XML directory: `backend/target/surefire-reports`.

## Result from Surefire XML

| Metric | Value |
|---|---:|
| Test suites | 11 |
| Tests | 71 |
| Passed | 71 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |

The totals were aggregated from the generated `TEST-*.xml` files and match Maven's final summary. The earlier Phase A result of 65 tests and the Phase B1 log remain historical evidence; neither is substituted for this final integration run.

## Test environments

These environments are separate and must not be presented as one system:

| Purpose | Database / cache | Configuration |
|---|---|---|
| Automated application tests | H2 in-memory, MySQL compatibility mode | `backend/src/test/resources/application.yml` |
| Cache integration tests | H2 plus Testcontainers `redis:7.4-alpine` | Cache enabled by test properties; TTL override `5s` |
| Runtime benchmark | Docker Compose MySQL 8.4.11 plus Redis 7.4-alpine | Production cache TTL `10m`; OFF/MISS/HIT measured in Phase B2 |

## Test inventory

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `BusinessExceptionTests` | 3 | 0 | 0 | 0 |
| `CategoryCrudTests` | 11 | 0 | 0 | 0 |
| `CourseCacheIntegrationTests` | 6 | 0 | 0 | 0 |
| `CourseControllerValidationTests` | 2 | 0 | 0 | 0 |
| `CourseCrudTests` | 10 | 0 | 0 | 0 |
| `CourseManagementApplicationTests` | 2 | 0 | 0 | 0 |
| `EnrollmentCrudTests` | 14 | 0 | 0 | 0 |
| `GlobalExceptionHandlerTests` | 7 | 0 | 0 | 0 |
| `RepositoryIntegrationTests` | 2 | 0 | 0 | 0 |
| `RequestDtoValidationTests` | 3 | 0 | 0 | 0 |
| `StudentCrudTests` | 11 | 0 | 0 | 0 |

## Regression matrix

| ID | Area | Scenario | Evidence | Result |
|---|---|---|---|---|
| APP-01 | Context | Application context and health controller load | `CourseManagementApplicationTests` | PASS |
| CAT-01 | Category | CRUD, validation, duplicate name and delete guard | `CategoryCrudTests` | PASS |
| CRS-01 | Course | CRUD, detail lookup, missing Category and delete guard | `CourseCrudTests` | PASS |
| CRS-02 | Course validation | Invalid create/update stops before service | `CourseControllerValidationTests` | PASS |
| STU-01 | Student | CRUD, validation, duplicate email and delete guard | `StudentCrudTests` | PASS |
| ENR-01 | Enrollment | Successful create links the selected Student and Course and API returns 201 | `EnrollmentCrudTests` | PASS |
| ENR-02 | Enrollment | Missing Student or Course prevents repository save | `EnrollmentCrudTests` | PASS |
| ENR-03 | Enrollment | Duplicate pair is rejected by service/API and database constraint | CRUD/business/repository tests | PASS |
| ENR-04 | Enrollment validation | Invalid create/update returns 400 before service | `EnrollmentCrudTests` | PASS |
| REL-01 | JPA relations | Course-Category and Enrollment-Student/Course queries | `RepositoryIntegrationTests` | PASS |
| EXC-01 | Exception | Validation and business errors use standardized HTTP responses | `BusinessExceptionTests`, `GlobalExceptionHandlerTests` | PASS |
| CAC-01 | Cache MISS/HIT | MISS reads DB, creates readable JSON and positive TTL; HIT adds no SQL statement | `missThenHitStoresReadableJsonAndAvoidsMoreSql` | PASS |
| CAC-02 | Cache expiry | Entry expires and the next read accesses DB again | `expiredEntryTriggersAnotherDatabaseRead` | PASS |
| CAC-03 | Course invalidation | Successful update/delete evicts after commit and subsequent reads are current | `updatingAndDeletingCourseEvictsOnlyAfterSuccessfulCalls` | PASS |
| CAC-04 | Category invalidation | Rename clears cached `categoryName` and next GET returns the new name | `renamingCategoryRefreshesCachedCategoryName` | PASS |
| CAC-05 | Rollback | Rolled-back update retains the previous cache entry | `rolledBackCourseUpdateLeavesCachedResponseIntact` | PASS |
| CAC-06 | Transaction timing | Outer transaction retains cache until commit, then evicts | `outerTransactionEvictsOnlyAfterCommit` | PASS |
| RUN-01 | Runtime benchmark | Same Course/commit/payload in OFF, MISS and HIT modes | Three summaries and comparison at commit `2aa11d2` | PASS |
| DEMO-01 | Course demo | Isolated MySQL schema, port 8081, health, Course list and detail | `reports/week4/course-demo-reproducibility.md` | PASS |

## Cache integration evidence

`CourseCacheIntegrationTests` completed `6` tests with no failure, error or skip. The suite took `32.12s` and used an actual Redis 7.4-alpine Testcontainer.

Observed log evidence includes:

- readable JSON stored at `courseDetails::<id>`;
- MISS and HIT ending with the same Hibernate statement count after the cached call;
- positive TTL under the 5-second test override;
- database reload after TTL expiry;
- key removal after Course update/delete and Category rename.

The rollback and outer-transaction assertions are recorded in Surefire XML as passing test cases even though they do not print custom console lines.

## Runtime benchmark evidence

- Endpoint: `GET /api/courses/1`.
- Workload: 20 warm-up and 100 measured requests, concurrency 1.
- Payload SHA-256: `f66005bb73b459e336348f33aa2368d853992d17d396aeb05af6ab121cef1f64` in all modes.
- OFF: median `22.540ms`, P95 `41.520ms`, DB SELECT delta `201`.
- MISS: median `29.204ms`, P95 `86.971ms`, DB SELECT delta `201`.
- HIT: median `8.883ms`, P95 `15.909ms`, DB SELECT delta `1`.
- Comparison: `scripts/cache-performance/results/comparison-2026-10-04_170517.md`.

This is integration-branch evidence only. The same regression and benchmark must be rerun after the real merge into `develop`.

## Course demo reproducibility

The standalone Course demo at commit `e303057cee465dc0c7f17567860c33b6a3607d45` compiled, packaged and ran on port `8081` against an isolated timestamped MySQL database. Health returned `UP`; Course list returned IDs `1,2,3`; Course detail returned ID `1`. The backend then stopped, the temporary database was dropped and the worktree was removed.

Evidence: `reports/week4/course-demo-reproducibility.md`.

## Warnings observed

- Mockito warned that dynamic agent self-attachment may be restricted by a future JDK. It did not fail this run.
- The expected unique-constraint warning occurred when the repository test deliberately inserted a duplicate Enrollment pair; the assertion passed.
- Spring's default Open EntityManager in View warning did not cause a regression failure.

No qualifying application bug was found during this regression, and no production code was changed to make it pass.

## Phase B2 integration status

- Final integrated regression: `PASS` (`71/71`).
- Cache integration regression: `PASS` (`6/6`).
- Runtime Course demo: `REPRODUCIBLE`.
- Official integration OFF/MISS/HIT benchmark: `PASS` and comparison validated.
- Final develop rerun after merge: required.
