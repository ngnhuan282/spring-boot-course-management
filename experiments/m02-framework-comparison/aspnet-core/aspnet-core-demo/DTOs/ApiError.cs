namespace aspnet_core_demo.DTOs;

public record ApiError(
    int Status,
    string Error,
    string Message
);