package com.ccnlthd.course_management.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    VALIDATION_FAILED("VALIDATION_FAILED", "Request data is invalid", HttpStatus.BAD_REQUEST),
    CATEGORY_NOT_FOUND("CATEGORY_NOT_FOUND", "Category not found", HttpStatus.NOT_FOUND),
    CATEGORY_ALREADY_EXISTS("CATEGORY_ALREADY_EXISTS", "Category name already exists", HttpStatus.CONFLICT),
    CATEGORY_HAS_COURSES("CATEGORY_HAS_COURSES", "Category has courses", HttpStatus.CONFLICT),
    COURSE_NOT_FOUND("COURSE_NOT_FOUND", "Course not found", HttpStatus.NOT_FOUND),
    COURSE_HAS_ENROLLMENTS("COURSE_HAS_ENROLLMENTS", "Course has enrollments", HttpStatus.CONFLICT),
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
}
