package com.mariosmant.fintech.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger configuration.
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fintechOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("POC Fintech API")
                        .version("1.0.0")
                        .description("""
                                Production-grade fintech Proof of Concept API.
                                Demonstrates: Hexagonal Architecture, DDD, CQRS,
                                Saga Orchestration, Transactional Outbox,
                                Double-Entry Ledger, Multi-Currency FX, Fraud Detection.
                                """)
                        .contact(new Contact()
                                .name("mariosmant")
                                .url("https://github.com/mariosmant")));
    }
}

