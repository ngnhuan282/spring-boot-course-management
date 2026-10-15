using Microsoft.AspNetCore.Mvc;

namespace aspnet_core_demo.Controllers;

[ApiController]
[Route("api")]
public class HealthController : ControllerBase
{
    [HttpGet("health")]
    public IActionResult Health()
    {
        return Ok(new
        {
            status = "UP",
            framework = "ASP.NET Core"
        });
    }
}