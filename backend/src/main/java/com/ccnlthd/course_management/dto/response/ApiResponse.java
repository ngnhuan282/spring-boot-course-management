package com.ccnlthd.course_management.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private String code;
    private String message;
    private T result;
    private LocalDateTime timestamp;

    public static <T> ApiResponse<T> success(T result) {
        return new ApiResponse<>("SUCCESS", "Success", result, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null, LocalDateTime.now());
    }
}
