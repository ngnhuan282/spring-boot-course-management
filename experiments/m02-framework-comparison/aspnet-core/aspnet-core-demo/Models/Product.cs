namespace aspnet_core_demo.Models;

public record Product(
    long Id,
    string Name,
    decimal Price
);