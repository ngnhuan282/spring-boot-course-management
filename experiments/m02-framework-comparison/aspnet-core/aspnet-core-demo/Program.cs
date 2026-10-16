using aspnet_core_demo.DTOs;
using aspnet_core_demo.Exceptions;
using aspnet_core_demo.Services;
using Microsoft.AspNetCore.Mvc;

var builder = WebApplication.CreateBuilder(args);

builder.Services
    .AddControllers()
    .ConfigureApiBehaviorOptions(options =>
    {
        options.InvalidModelStateResponseFactory = context =>
        {
            var message = context.ModelState
                .Where(x => x.Value?.Errors.Count > 0)
                .SelectMany(x =>
                    x.Value!.Errors.Select(error =>
                        $"{x.Key}: {error.ErrorMessage}"
                    )
                )
                .FirstOrDefault()
                ?? "Invalid request";

            var response = new ApiError(
                400,
                "VALIDATION_FAILED",
                message
            );

            return new BadRequestObjectResult(response);
        };
    });

builder.Services.AddSingleton<ProductService>();

builder.Services.AddExceptionHandler<GlobalExceptionHandler>();
builder.Services.AddProblemDetails();

builder.WebHost.UseUrls("http://localhost:8081");

var app = builder.Build();

app.UseExceptionHandler();

app.MapControllers();

app.Run();