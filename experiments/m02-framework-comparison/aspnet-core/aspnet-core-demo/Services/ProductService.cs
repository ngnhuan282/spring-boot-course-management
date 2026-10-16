using aspnet_core_demo.DTOs;
using aspnet_core_demo.Exceptions;
using aspnet_core_demo.Models;

namespace aspnet_core_demo.Services;

public class ProductService
{
    private readonly List<Product> _products = new()
    {
        new Product(1, "Laptop", 15000000),
        new Product(2, "Mouse", 300000)
    };

    private long _nextId = 3;

    public List<Product> GetAll()
    {
        return _products;
    }

    public Product GetById(long id)
    {
        return _products.FirstOrDefault(p => p.Id == id)
               ?? throw new ProductNotFoundException(id);
    }

    public Product Create(ProductRequest request)
    {
        var product = new Product(
            _nextId++,
            request.Name,
            request.Price
        );

        _products.Add(product);

        return product;
    }

    public Product Update(long id, ProductRequest request)
    {
        var oldProduct = GetById(id);

        var updatedProduct = new Product(
            oldProduct.Id,
            request.Name,
            request.Price
        );

        var index = _products.IndexOf(oldProduct);

        _products[index] = updatedProduct;

        return updatedProduct;
    }

    public void Delete(long id)
    {
        var product = GetById(id);

        _products.Remove(product);
    }
}