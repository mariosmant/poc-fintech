package com.mariosmant.fintech.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger configuration.
 *
 * <p>Declares the {@code bearer-jwt} HTTP Bearer security scheme referenced by
 * {@code @SecurityRequirement(name = "bearer-jwt")} on every protected controller.
 * Without this declaration the Swagger UI "Authorize" button cannot present a
 * matching input and every call from Swagger is unauthenticated.</p>
 *
 * <p>Also applies {@code bearer-jwt} as a <em>global</em> security requirement so
 * operations inherit it by default; individual endpoints may still override with
 * their own {@code @SecurityRequirements} when publicly accessible.</p>
 *
 * @since 1.0.0
 */
@Configuration
public class OpenApiConfig {

    /** Scheme name — referenced from controllers via {@code @SecurityRequirement(name = BEARER_JWT_SCHEME)}. */
    public static final String BEARER_JWT_SCHEME = "bearer-jwt";

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
                                .url("https://github.com/mariosmant")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_JWT_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("""
                                        Keycloak-issued JWT access token. In the Swagger
                                        "Authorize" dialog paste the raw token value
                                        (without the 'Bearer ' prefix).
                                        """)))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_JWT_SCHEME));
    }
}
