package com.ccnlthd.course_management.exception;

import lombok.Getter;

public class AppException extends RuntimeException {

    @Getter
    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
