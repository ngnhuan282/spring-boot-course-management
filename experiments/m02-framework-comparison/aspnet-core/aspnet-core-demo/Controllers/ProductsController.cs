using aspnet_core_demo.DTOs;
using aspnet_core_demo.Models;
using aspnet_core_demo.Services;
using Microsoft.AspNetCore.Mvc;

namespace aspnet_core_demo.Controllers;

[ApiController]
[Route("api/products")]
public class ProductsController : ControllerBase
{
    private readonly ProductService _productService;
    public ProductsController(ProductService productService)
    {
        _productService = productService;
    }
    [HttpGet]
    public ActionResult<List<Product>> GetAll()
    {
        return Ok(_productService.GetAll());
    }

    [HttpGet("{id:long}")]
    public ActionResult<Product> GetById(long id)
    {
        return Ok(_productService.GetById(id));
    }

    [HttpPost]
    public ActionResult<Product> Create(ProductRequest request)
    {
        var product = _productService.Create(request);

        return StatusCode(201, product);
    }

    [HttpPut("{id:long}")]
    public ActionResult<Product> Update(
        long id,
        ProductRequest request)
    {
        return Ok(
            _productService.Update(id, request)
        );
    }

    [HttpDelete("{id:long}")]
    public IActionResult Delete(long id)
    {
        _productService.Delete(id);

        return NoContent();
    }
}