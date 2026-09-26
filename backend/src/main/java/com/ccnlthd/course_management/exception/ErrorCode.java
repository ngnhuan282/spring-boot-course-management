package com.ccnlthd.course_management.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    VALIDATION_FAILED("VALIDATION_FAILED", "Request data is invalid", HttpStatus.BAD_REQUEST),
    COURSE_NOT_FOUND("COURSE_NOT_FOUND", "Course not found", HttpStatus.NOT_FOUND),
    UNCATEGORIZED_EXCEPTION(
            "UNCATEGORIZED_EXCEPTION",
            "An unexpected error occurred",
            HttpStatus.INTERNAL_SERVER_ERROR
    );

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
