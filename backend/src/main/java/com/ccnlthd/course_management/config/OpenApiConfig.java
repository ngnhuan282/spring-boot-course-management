package com.ccnlthd.course_management.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI courseManagementOpenAPI() {

        return new OpenAPI()
                .info(
                        new Info()
                                .title("Course Management API")
                                .description(
                                        "REST API for Course Management System"
                                )
                                .version("1.0.0")
                );
    }
}
