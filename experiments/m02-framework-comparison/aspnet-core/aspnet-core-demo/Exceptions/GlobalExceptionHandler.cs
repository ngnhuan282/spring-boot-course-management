using aspnet_core_demo.DTOs;
using Microsoft.AspNetCore.Diagnostics;

namespace aspnet_core_demo.Exceptions;

public class GlobalExceptionHandler : IExceptionHandler
{
    public async ValueTask<bool> TryHandleAsync(
        HttpContext httpContext,
        Exception exception,
        CancellationToken cancellationToken)
    {
        if (exception is not ProductNotFoundException)
        {
            return false;
        }

        httpContext.Response.StatusCode =
            StatusCodes.Status404NotFound;

        var response = new ApiError(
            404,
            "NOT_FOUND",
            exception.Message
        );

        await httpContext.Response.WriteAsJsonAsync(
            response,
            cancellationToken
        );

        return true;
    }
}