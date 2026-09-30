# Week 3: API error responses

`GlobalExceptionHandler` returns the same JSON shape for every handled error:

```json
{
  "code": "COURSE_NOT_FOUND",
  "message": "Course not found",
  "result": null,
  "timestamp": "2026-09-30T10:00:00"
}
```

`timestamp` is generated when the response is created. Error responses do not expose stack traces or database details.

| HTTP status | Error code | When it is returned |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | Bean Validation rejects a request DTO, the JSON body is missing/malformed, or a path parameter has the wrong type. |
| 404 | `RESOURCE_NOT_FOUND` | No endpoint or static resource matches the requested path. |
| 404 | `CATEGORY_NOT_FOUND` | The referenced category does not exist. |
| 404 | `COURSE_NOT_FOUND` | The referenced course does not exist. |
| 404 | `STUDENT_NOT_FOUND` | The referenced student does not exist. |
| 404 | `ENROLLMENT_NOT_FOUND` | The referenced enrollment does not exist. |
| 409 | `CATEGORY_ALREADY_EXISTS` | A category name is already in use. |
| 409 | `CATEGORY_HAS_COURSES` | A category with courses cannot be deleted. |
| 409 | `COURSE_HAS_ENROLLMENTS` | A course with enrollments cannot be deleted. |
| 409 | `STUDENT_EMAIL_ALREADY_EXISTS` | A student email address is already in use. |
| 409 | `STUDENT_HAS_ENROLLMENTS` | A student with enrollments cannot be deleted. |
| 409 | `ENROLLMENT_ALREADY_EXISTS` | A student is already enrolled in the course. |
| 409 | `DATA_CONFLICT` | A database constraint rejects a write, including a concurrent write that bypassed a service precheck. |
| 500 | `UNCATEGORIZED_EXCEPTION` | An unexpected server error occurs. The exception is logged on the server. |

`GlobalExceptionHandlerTests` checks the response shape and status for validation, 404, 409 and 500 cases. Run locally with MySQL from `docker-compose.yml`, then execute `cd backend` and `sh ./mvnw verify` (or `mvnw.cmd verify` on Windows). The GitHub Actions workflow runs the same build and tests with its own MySQL service and uploads Surefire XML reports.
