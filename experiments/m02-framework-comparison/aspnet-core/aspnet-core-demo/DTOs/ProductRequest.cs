using System.ComponentModel.DataAnnotations;

namespace aspnet_core_demo.DTOs;

public class ProductRequest
{
    [Required(ErrorMessage = "Product name is required")]
    public string Name { get; set; } = string.Empty;

    [Range(0, double.MaxValue,
        ErrorMessage = "Product price must be zero or positive")]
    public decimal Price { get; set; }
}