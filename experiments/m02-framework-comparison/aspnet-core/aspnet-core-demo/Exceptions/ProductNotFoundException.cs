namespace aspnet_core_demo.Exceptions;

public class ProductNotFoundException : Exception
{
    public ProductNotFoundException(long id)
        : base($"Product with id {id} was not found")
    {
    }
}