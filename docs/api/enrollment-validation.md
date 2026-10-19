# Enrollment request validation

POST `/api/enrollments` and PUT `/api/enrollments/{id}` use the same required
request body. PUT replaces the request fields; omitted fields are invalid.

| Field | Contract |
| --- | --- |
| `studentId` | Required, non-null integer greater than zero |
| `courseId` | Required, non-null integer greater than zero |
| `status` | Required, exactly `ACTIVE` or `COMPLETED` |

Status is case-sensitive. Empty, lowercase, unknown and whitespace-padded values
are rejected without implicit normalization. `CourseRequest.categoryId` follows
the same required, positive reference-ID rule.

Example request (the referenced student and course must exist):

```json
{"studentId":2,"courseId":3,"status":"ACTIVE"}
```

Invalid bodies return HTTP 400 with `code: VALIDATION_FAILED`,
`message: Request data is invalid`, a `result` array of field/message entries,
and a timestamp. Several constraints can report errors for the same field;
error order is not part of the contract. Validation runs before the Service.
A positive but nonexistent reference is a separate business lookup error (404).
An existing student/course pair remains a conflict (409), regardless of status.

DTO `@Schema` annotations and controller operation/400 descriptions document
this contract in OpenAPI. Production and H2 test seeds use positive references
and include both accepted Enrollment statuses. Existing stored invalid values
are not migrated by this change.

## Verification

`EnrollmentValidationContractTests` exercise POST and PUT with missing/null
fields, zero/negative IDs, blank/unknown/lowercase/padded/oversized status and
both valid statuses. Mocked Services must receive no call for an invalid body.
Course category-ID cases cover both methods too.

`EnrollmentValidationDatabaseTests` use real Services and a private in-memory
H2 database. They compare all seeded category/course/student/enrollment rows
before and after representative invalid requests, without an enclosing test
transaction. A valid-update control proves that a request can reach the real
Service and commit a change. The control restores its private fixture afterward.

These are in-process MockMvc exchanges and H2 checks. They do not demonstrate a
running network server, MySQL behavior, Redis behavior or a complete project
test-suite run. Surefire XML and the actual Maven exit code determine the result.
When `-Dcv2.evidenceDir=<outside-repository-directory>` is supplied, the tests
also save actual requests, responses and database comparisons as UTF-8 text.
