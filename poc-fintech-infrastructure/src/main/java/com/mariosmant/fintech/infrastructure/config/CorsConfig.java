package com.mariosmant.fintech.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS configuration for the React TypeScript frontend.
 *
 * <p>Restricts cross-origin requests to the configured frontend origin.
 * In production, this should be set to the exact frontend domain
 * (e.g., {@code https://app.example.com}).</p>
 *
 * <p><b>Security alignment:</b>
 * <ul>
 *   <li>NIST SC-7: Boundary protection — restrict cross-origin access</li>
 *   <li>OWASP: Misconfigured CORS prevention</li>
 *   <li>SOC 2 CC6.1: Logical access control</li>
 * </ul></p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Configuration
@Profile("!test")
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    /**
     * Configures CORS for the API endpoints, allowing the React frontend
     * to communicate with the backend.
     *
     * @return the CORS configuration source
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Idempotency-Key",
                "X-Request-ID"
        ));
        config.setExposedHeaders(List.of(
                "X-Request-ID",
                // IETF draft-ietf-httpapi-ratelimit-headers.
                // only the IETF triplet + Retry-After are exposed.
                "RateLimit-Limit",
                "RateLimit-Remaining",
                "RateLimit-Reset",
                "Retry-After",
                "Authorization"
        ));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L); // Cache preflight for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}

