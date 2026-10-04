# Course Demo Reproducibility

## Result

- Status: `REPRODUCIBLE`.
- Source: `origin/refactor/course-standalone`.
- Commit: `e303057cee465dc0c7f17567860c33b6a3607d45` (`refactor(course): simplify standalone CRUD`).
- Runtime Java: `22.0.2`.
- Spring Boot: `4.1.1`.
- Runtime database: isolated MySQL database `course_management_demo_20261004_170900` on the existing Compose MySQL service.
- Runtime port: `8081`.

## Procedure and Evidence

1. A detached temporary Git worktree was created from the exact demo commit.
2. `backend/.\mvnw.cmd -DskipTests package` completed with `BUILD SUCCESS` in `10.431s`. The demo branch contains no test sources, so this step verifies compilation and packaging only.
3. A timestamped database was created specifically for this demo. The application used `ddl-auto=update` and `data.sql` only against that isolated database, never against `course_management`.
4. The backend started successfully on port `8081` and reported MySQL `8.4.11` with the isolated schema.
5. `GET /api/health` returned `UP`.
6. `GET /api/courses` returned three records with IDs `1`, `2` and `3`.
7. `GET /api/courses/1` returned Course `1`, title `Spring Boot Fundamentals`.
8. The backend shut down gracefully with Maven `BUILD SUCCESS`.
9. The timestamped database was dropped and its absence was verified through `INFORMATION_SCHEMA`.
10. The temporary worktree was removed; `git worktree list` contains only the integration worktree.

## Isolation

The demo was a reproducibility experiment only. It did not merge the demo branch, modify the integration branch, or migrate/seed the primary `course_management` database.
