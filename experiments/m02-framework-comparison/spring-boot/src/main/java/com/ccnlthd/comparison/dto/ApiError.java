package com.ccnlthd.comparison.dto;

public record ApiError(
        int status,
        String error,
        String message
) {
}