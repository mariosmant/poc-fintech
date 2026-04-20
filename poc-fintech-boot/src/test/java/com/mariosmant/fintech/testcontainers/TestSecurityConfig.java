package com.mariosmant.fintech.testcontainers;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Test security configuration — permits all requests and provides a mock JwtDecoder.
 * Used for integration and E2E tests where Keycloak is not available.
 *
 * @author mariosmant
 * @since 1.0.0
 */
@TestConfiguration(proxyBeanMethods = false)
@Profile("test")
public class TestSecurityConfig {

    /** Stable test user ID — ensures all requests in a test session identify as the same user. */
    public static final String TEST_USER_ID = "test-user-fixed-id";

    /**
     * Overrides the main security filter chain to permit all requests in tests.
     */
    @Bean
    @Primary
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Configure OAuth2 resource server with mock JwtDecoder so Bearer tokens
                // create a proper JwtAuthenticationToken in the SecurityContext
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; frame-ancestors 'none'; form-action 'self'"))
                        .referrerPolicy(ref -> ref.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicy(pp -> pp.policy(
                                "camera=(), microphone=(), geolocation=(), payment=(self)")))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Mock JwtDecoder that accepts any token and returns a stable test user identity.
     * Prevents auto-configuration from trying to contact Keycloak during tests.
     * Uses a fixed user ID so ownership checks pass within a test session.
     */
    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        return token -> {
            Instant now = Instant.now();
            return new Jwt(
                    token,
                    now.minusSeconds(60),
                    now.plusSeconds(3600),
                    Map.of("alg", "RS256"),
                    Map.of(
                            "sub", TEST_USER_ID,
                            "preferred_username", "test-user",
                            "iss", "http://localhost:18180/realms/fintech",
                            "aud", List.of("poc-fintech-bff"),
                            "realm_access", Map.of("roles", List.of("user"))
                    )
            );
        };
    }
}





