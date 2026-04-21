package com.mariosmant.fintech.testcontainers;

import com.mariosmant.fintech.infrastructure.security.KeycloakJwtAuthoritiesConverter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
 * Test security configuration — permits all requests at the filter-chain level,
 * but keeps method-security ({@code @PreAuthorize}) active so integration tests
 * exercise the same authorization rules as production.
 *
 * <p>The mock {@link JwtDecoder} emits a token carrying {@code realm_access.roles=[user]},
 * and the filter chain reuses the production
 * {@link KeycloakJwtAuthoritiesConverter} so the resulting
 * {@code JwtAuthenticationToken} carries {@code ROLE_USER}. This makes
 * {@code hasRole('USER')} pass end-to-end in tests without contacting Keycloak.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@TestConfiguration(proxyBeanMethods = false)
@EnableMethodSecurity
@Profile("test")
public class TestSecurityConfig {

    /** Stable test user ID — ensures all requests in a test session identify as the same user. */
    public static final String TEST_USER_ID = "test-user-fixed-id";

    /**
     * Overrides the main security filter chain: permits all requests at the URL level,
     * but plugs in the Keycloak authorities converter so method-security sees
     * {@code ROLE_USER} on the authenticated principal.
     */
    @Bean
    @Primary
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Use the production Keycloak converter so realm_access.roles → ROLE_*
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                KeycloakJwtAuthoritiesConverter.jwtAuthenticationConverter())))
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

    // ...existing code...

    /**
     * Mock JwtDecoder that accepts any token and returns a stable test user identity.
     * Prevents auto-configuration from trying to contact Keycloak during tests.
     * Uses a fixed user ID so ownership checks pass within a test session.
     *
     * <p>Claims mirror the hardened production validation contract
     * ({@code JwtValidators}): {@code aud=poc-fintech-api}, {@code azp=poc-fintech-bff},
     * {@code typ=JWT} header, and a non-null {@code iat}. This keeps the mock
     * semantically aligned with the production decoder even though the test profile
     * bypasses the strict validator chain.</p>
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
                    Map.of("alg", "RS256", "typ", "JWT"),
                    Map.of(
                            "sub", TEST_USER_ID,
                            "iat", now.minusSeconds(60),
                            "exp", now.plusSeconds(3600),
                            "preferred_username", "test-user",
                            "iss", "http://localhost:18180/realms/fintech",
                            "aud", List.of("poc-fintech-api"),
                            "azp", "poc-fintech-bff",
                            "realm_access", Map.of("roles", List.of("user"))
                    )
            );
        };
    }
}





