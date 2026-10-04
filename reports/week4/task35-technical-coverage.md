# Task 35 - Technical Coverage

## Snapshot analyzed

- Branch: `feat/week4-cache-coverage-regression`.
- Integration measurement commit: `2aa11d2aff81dab06dc0804016caf4f9e4425b28`.
- Measurement source working tree: Clean for OFF, MISS and HIT.
- Current working tree: Dirty only because Phase B2 evidence has not been committed.
- Final product commit: `PENDING_FINAL_PRODUCT_COMMIT`.
- Final regression evidence time: `2026-10-04T17:07:54+07:00`.
- Local Java: `22.0.2`; Maven target and CI Java: `21`.

This is an integration-candidate snapshot. It must not be presented as the final develop commit.

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
| 10 | Docker / datasource reproducibility | T2 | Compose MySQL 8.4 and Redis 7.4-alpine; environment-based datasource/cache config | Runtime benchmark plus isolated Course demo | Integrated | Final develop rerun remains required |
| 11 | Spring Cache + Redis | T3 | Course detail cache, JSON response serialization, TTL and after-commit invalidation | Six Testcontainers Redis tests plus official integration OFF/MISS/HIT | Integrated | Integration measurement is not a final product claim |

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
- Final Phase B2 integration log: `reports/week4/raw/regression-integration-final-2026-10-04_170645.log`.
- Automated application datasource: H2 in-memory with MySQL compatibility mode.
- Cache integration backend: Testcontainers `redis:7.4-alpine` with a test-only TTL of 5 seconds.

The repository tests did not run directly against runtime MySQL. Runtime benchmark evidence used MySQL 8.4.11 and Redis 7.4-alpine from Docker Compose with the production TTL of 10 minutes.

### Cache / Redis

- `CourseServiceImpl#getCourseById`: `@Cacheable(cacheNames = "courseDetails", key = "#id")`.
- Redis key: `courseDetails::<id>`.
- `CourseCacheConfig`: `JacksonJsonRedisSerializer<CourseResponse>`, no null caching, property-driven TTL and transaction-aware cache manager.
- `CourseDetailCacheInvalidator`: one-key eviction or full cache invalidation after transaction commit.
- Course update/delete evicts one key; Category rename invalidates all Course detail entries.
- `CourseCacheIntegrationTests`: MISS/HIT, JSON, TTL expiry, update/delete, Category rename, rollback and outer transaction timing.
- `scripts/cache-performance/benchmark.ps1`: measured OFF/MISS/HIT at commit `2aa11d2aff81dab06dc0804016caf4f9e4425b28`.
- `scripts/cache-performance/results/comparison-2026-10-04_170517.md`: validated same commit, endpoint, Course ID, workload, machine metadata and payload hash.
- HIT median improved `60.590%` versus OFF and `69.583%` versus MISS; HIT DB SELECT delta was `1` versus `201` for OFF/MISS.

### CI and reproducibility

- CI runs on `ubuntu-latest` with Java 21 and `sh ./mvnw -B verify`.
- CI has no MySQL service. Normal automated tests use the H2 test datasource.
- The cache integration suite uses Testcontainers Redis and requires Docker support on the runner.
- CI uploads Surefire XML, but no successful GitHub Actions run URL or screenshot was inspected in this session.
- README now documents implemented technologies, the real package path, Maven test command and Docker requirement for cache tests.

## Phase B2 status

- Technical coverage: `INTEGRATION_CANDIDATE_COMPLETE`.
- Cache implementation: `Integrated`, verified by Testcontainers Redis and runtime benchmark.
- Course demo: `REPRODUCIBLE` at commit `e303057cee465dc0c7f17567860c33b6a3607d45` using an isolated database.
- CI run success: `NEEDS_EXTERNAL_GITHUB_EVIDENCE`.
- Final product commit: `PENDING_FINAL_PRODUCT_COMMIT`.
- Final develop rerun after merge: required.
