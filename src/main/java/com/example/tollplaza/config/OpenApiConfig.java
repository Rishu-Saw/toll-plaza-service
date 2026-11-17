package com.example.tollplaza.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Toll Plazas Between Two Pincodes API")
                        .version("1.0.0")
                        .description("Spring Boot REST API that calculates the road route between two Indian pincodes and returns toll plazas along the route with their exact distances from the source.")
                        .contact(new Contact().name("Backend Assignment Team")));
    }
}
