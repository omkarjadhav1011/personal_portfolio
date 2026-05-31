package com.portfolio.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI portfolioOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Portfolio API")
                        .description("REST API for the Git-themed developer portfolio backend")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Omkar Jadhav")
                                .email("omkar.jadhav@nonstopio.com")));
    }
}
