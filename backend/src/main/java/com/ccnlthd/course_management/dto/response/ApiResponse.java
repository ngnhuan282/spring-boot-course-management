package com.ccnlthd.course_management.dto.response;

import java.time.LocalDateTime;

public class ApiResponse<T> {

    private String code;
    private String message;
    private T result;
    private LocalDateTime timestamp;

    public ApiResponse() {
    }

    public ApiResponse(String code, String message, T result, LocalDateTime timestamp) {
        this.code = code;
        this.message = message;
        this.result = result;
        this.timestamp = timestamp;
    }

    public static <T> ApiResponse<T> success(T result) {
        return new ApiResponse<>("SUCCESS", "Success", result, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null, LocalDateTime.now());
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getResult() {
        return result;
    }

    public void setResult(T result) {
        this.result = result;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
