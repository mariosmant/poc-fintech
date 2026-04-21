package com.mariosmant.fintech.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.web.cors.CorsConfigurationSource;


/**
 * Security configuration aligned with NIST SP 800-53, SOG-IS Crypto Evaluation Scheme,
 * and SOC 2 Type II compliance controls.
 *
 * <p>Integrates Keycloak as OAuth2/OIDC identity provider via JWT resource server.
 * All API endpoints require authentication. Actuator and Swagger are public.</p>
 *
 * <p><b>Applied security controls:</b></p>
 * <table>
 *   <tr><th>Header / Policy</th><th>Standard</th><th>Rationale</th></tr>
 *   <tr><td>HSTS (1 year, includeSubDomains, preload)</td><td>NIST SC-8</td>
 *       <td>Enforces TLS for all connections</td></tr>
 *   <tr><td>X-Content-Type-Options: nosniff</td><td>OWASP</td>
 *       <td>Prevents MIME-type sniffing attacks</td></tr>
 *   <tr><td>X-Frame-Options: DENY</td><td>OWASP</td>
 *       <td>Prevents clickjacking (framing) attacks</td></tr>
 *   <tr><td>Content-Security-Policy</td><td>NIST SC-18</td>
 *       <td>Restricts resource loading to prevent XSS/injection</td></tr>
 *   <tr><td>Referrer-Policy: strict-origin-when-cross-origin</td><td>SOC 2 CC6.1</td>
 *       <td>Limits referrer information leakage</td></tr>
 *   <tr><td>Permissions-Policy</td><td>SOC 2 CC6.6</td>
 *       <td>Disables unnecessary browser features</td></tr>
 *   <tr><td>Cache-Control: no-store</td><td>NIST SC-28</td>
 *       <td>Prevents caching of sensitive financial data</td></tr>
 *   <tr><td>Stateless sessions + OAuth2 JWT</td><td>NIST IA-2</td>
 *       <td>No server-side session state; Keycloak JWT-based auth</td></tr>
 *   <tr><td>CSRF disabled</td><td>—</td>
 *       <td>Stateless REST API with Bearer token auth (not cookie-based)</td></tr>
 * </table>
 *
 * @author mariosmant
 * @see <a href="https://csrc.nist.gov/publications/detail/sp/800-53/rev-5/final">NIST SP 800-53 Rev 5</a>
 * @since 1.0.0
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("!test")
public class SecurityConfig {

    /**
     * Configures the HTTP security filter chain with production-grade
     * security headers, OAuth2 resource server, and policies.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                // ── CORS (for React frontend + Keycloak) ──────────────────
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                // ── Session management (NIST IA-2) ───────────────────────
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ── CSRF (disabled for stateless Bearer-token REST API) ──
                .csrf(AbstractHttpConfigurer::disable)

                // ── OAuth2 Resource Server (JWT from Keycloak) ───────────
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))

                // ── Security headers ─────────────────────────────────────
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .preload(true)
                                .maxAgeInSeconds(31_536_000))
                        .contentTypeOptions(contentType -> {
                        })
                        .frameOptions(frame -> frame.deny())
                        .xssProtection(xss ->
                                xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.DISABLED))
                        .referrerPolicy(referrer ->
                                referrer.policy(ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(
                                        "default-src 'self'; " +
                                        "script-src 'self'; " +
                                        "style-src 'self' 'unsafe-inline'; " +
                                        "img-src 'self' data:; " +
                                        "font-src 'self'; " +
                                        "connect-src 'self' http://localhost:8180; " +
                                        "frame-ancestors 'none'; " +
                                        "form-action 'self'; " +
                                        "base-uri 'self'"))
                        .permissionsPolicy(permissions -> permissions
                                .policy("geolocation=(), camera=(), microphone=(), " +
                                        "payment=(), usb=(), magnetometer=()"))
                )

                // ── Authorization rules ──────────────────────────────────
                .authorizeHttpRequests(auth -> auth
                        // Actuator health/info — always accessible for load balancers
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info"
                        ).permitAll()
                        // Prometheus metrics — permit for scraping (restrict via network in prod)
                        .requestMatchers("/actuator/prometheus").permitAll()
                        // OpenAPI / Swagger UI — permit for POC
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()
                        // CORS preflight
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // All API endpoints require authentication (hostile environment)
                        .requestMatchers("/api/**").authenticated()
                        // All other requests require authentication
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    /**
     * Converts Keycloak JWT claims to Spring Security authorities.
     * Extracts roles from both {@code realm_access.roles} and
     * {@code resource_access.<client>.roles}. Logic is shared with the
     * test filter chain via {@link KeycloakJwtAuthoritiesConverter}, so
     * {@code @PreAuthorize("hasRole('USER')")} checks behave identically
     * in production and in integration tests.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        return KeycloakJwtAuthoritiesConverter.jwtAuthenticationConverter();
    }
}
