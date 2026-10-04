# Task 35 - Technical Coverage

## Snapshot analyzed

- Branch: `feat/week4-cache-coverage-regression`.
- Base HEAD: `0470809a8db62694d390b179d46281e70d985df8`.
- Base commit subject: `fix(ci): use H2 datasource for tests`.
- Working tree: Dirty - TV2 integration work is not committed.
- Final product commit: `PENDING_FINAL_PRODUCT_COMMIT`.
- Regression evidence time: `2026-10-04T16:23:13+07:00`.
- Local Java: `22.0.2`; Maven target and CI Java: `21`.

This is a Phase B1 snapshot. It must not be presented as the final develop commit.

## Coverage table

| # | Technique | Tier | Implementation evidence | Verification | Status | Limitation |
|---:|---|---|---|---|---|---|
| 1 | IoC / DI | T1 | Constructor injection through Lombok `@RequiredArgsConstructor` in controllers and services | Spring context and CRUD tests | Integrated | Dependency wiring is framework-managed |
| 2 | Controller-Service layering | T1 | Category, Course, Student and Enrollment controllers delegate to service interfaces | MockMvc and service tests | Integrated | No direct repository access from controllers |
| 3 | Spring Data JPA | T1 | Entities, repositories, relations and derived/custom queries | H2 repository and service tests | Integrated | Automated datasource is H2 in MySQL mode |
| 4 | Bean Validation | Optional | Request DTO constraints and `@Valid` controller parameters | Validation tests return HTTP 400 before service calls | Integrated | Runtime manual request remains Phase B2 evidence |
| 5 | Exception handling | Optional | `AppException`, `ErrorCode`, `GlobalExceptionHandler`, `ApiResponse` | Business and handler tests | Integrated | HTTP behavior verified with MockMvc |
| 6 | Git workflow | T2 | Feature branches and conventional commits in repository history | Branch/HEAD inspection | Integrated | Final merge/PR evidence is external |
| 7 | Automated testing | T2 | Unit, service, repository, MockMvc and cache integration tests | `71/71` passed on 2026-10-04 | Integrated | Local run used JDK 22.0.2 |
| 8 | CI | T2 | `.github/workflows/ci.yml`: Ubuntu, Java 21, Maven verify, Surefire upload | Workflow configuration inspected | Configured | GitHub run success needs external evidence |
| 9 | Swagger / OpenAPI | T2 | Springdoc dependency and `/swagger-ui/index.html` documentation | Source and README inspection | Integrated | Live UI screenshot remains runtime evidence |
| 10 | Docker / datasource reproducibility | T2 | Compose MySQL 8.4 and Redis 7.4-alpine; environment-based datasource/cache config | Compose and configuration inspection | Integrated | Runtime Course demo remains Phase B2 |
| 11 | Spring Cache + Redis | T3 | Course detail cache, JSON response serialization, TTL and after-commit invalidation | Six Testcontainers Redis tests passed | Integrated - benchmark pending | Official OFF/MISS/HIT remains Phase B2 |

Task31 runtime measurement is evidence for the Cache/Redis row; it is not counted as a separate T3 technique.

## Traceability

### T1 and optional techniques

- IoC/DI and layering: `CourseController`, `CourseServiceImpl`, `EnrollmentController`, `EnrollmentServiceImpl`, `CategoryController`, `CategoryServiceImpl`, `StudentController` and `StudentServiceImpl`.
- JPA: `Course`, `Enrollment`, `CourseRepository` and `EnrollmentRepository`, including Course-Category and Enrollment-Student/Course relations.
- Validation: `CourseRequest`, `EnrollmentRequest`, `CourseControllerValidationTests`, `RequestDtoValidationTests` and invalid-request cases in CRUD tests.
- Exception handling: `GlobalExceptionHandler`, `BusinessExceptionTests` and `GlobalExceptionHandlerTests`.

All paths above are under `backend/src/main/java/com/ccnlthd/course_management` or the matching test package.

### Testing

- Command: `.\mvnw.cmd clean test` from `backend`.
- Result: `71` tests, `0` failures, `0` errors, `0` skipped.
- Official Phase B1 log: `reports/week4/raw/regression-integration-2026-10-04_162137.log`.
- Automated application datasource: H2 in-memory with MySQL compatibility mode.
- Cache integration backend: Testcontainers `redis:7.4-alpine` with a test-only TTL of 5 seconds.

The repository tests did not run directly against runtime MySQL. Runtime benchmark evidence will use MySQL 8.4 and Redis 7.4-alpine from Docker Compose with the production TTL of 10 minutes.

### Cache / Redis

- `CourseServiceImpl#getCourseById`: `@Cacheable(cacheNames = "courseDetails", key = "#id")`.
- Redis key: `courseDetails::<id>`.
- `CourseCacheConfig`: `JacksonJsonRedisSerializer<CourseResponse>`, no null caching, property-driven TTL and transaction-aware cache manager.
- `CourseDetailCacheInvalidator`: one-key eviction or full cache invalidation after transaction commit.
- Course update/delete evicts one key; Category rename invalidates all Course detail entries.
- `CourseCacheIntegrationTests`: MISS/HIT, JSON, TTL expiry, update/delete, Category rename, rollback and outer transaction timing.
- `scripts/cache-performance/benchmark.ps1`: prepared for official OFF/MISS/HIT measurement after the Phase B1 commit.

### CI and reproducibility

- CI runs on `ubuntu-latest` with Java 21 and `sh ./mvnw -B verify`.
- CI has no MySQL service. Normal automated tests use the H2 test datasource.
- The cache integration suite uses Testcontainers Redis and requires Docker support on the runner.
- CI uploads Surefire XML, but no successful GitHub Actions run URL or screenshot was inspected in this session.
- README now documents implemented technologies, the real package path, Maven test command and Docker requirement for cache tests.

## Phase B1 status

- Cache implementation: `Integrated` and verified by Testcontainers Redis.
- Cache benchmark: `PENDING_PHASE_B2`.
- CI run success: `NEEDS_EXTERNAL_GITHUB_EVIDENCE`.
- Final product commit: `PENDING_FINAL_PRODUCT_COMMIT`.
- Final develop rerun after merge: required.
