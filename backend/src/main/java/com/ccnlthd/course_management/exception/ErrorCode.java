package com.ccnlthd.course_management.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_FAILED("VALIDATION_FAILED", "Request data is invalid", HttpStatus.BAD_REQUEST),
    CATEGORY_NOT_FOUND("CATEGORY_NOT_FOUND", "Category not found", HttpStatus.NOT_FOUND),
    COURSE_NOT_FOUND("COURSE_NOT_FOUND", "Course not found", HttpStatus.NOT_FOUND),
    STUDENT_NOT_FOUND("STUDENT_NOT_FOUND", "Student not found", HttpStatus.NOT_FOUND),
    ENROLLMENT_ALREADY_EXISTS(
            "ENROLLMENT_ALREADY_EXISTS",
            "Student already enrolled in this course",
            HttpStatus.CONFLICT
    ),
    UNCATEGORIZED_EXCEPTION(
            "UNCATEGORIZED_EXCEPTION",
            "An unexpected error occurred",
            HttpStatus.INTERNAL_SERVER_ERROR
    );

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
