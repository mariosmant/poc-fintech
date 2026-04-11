package com.mariosmant.fintech.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Security configuration aligned with NIST SP 800-53, SOG-IS Crypto Evaluation Scheme,
 * and SOC 2 Type II compliance controls.
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
 *   <tr><td>Stateless sessions</td><td>NIST IA-2</td>
 *       <td>No server-side session state; token-based auth in production</td></tr>
 *   <tr><td>CSRF disabled</td><td>—</td>
 *       <td>Stateless REST API; mitigated by token-based auth</td></tr>
 * </table>
 *
 * <p><b>Production note:</b> In a production deployment, add OAuth 2.0 / JWT
 * resource server, mTLS for service-to-service, rate limiting via API gateway,
 * and IP allowlisting for admin endpoints.</p>
 *
 * @author mariosmant
 * @see <a href="https://csrc.nist.gov/publications/detail/sp/800-53/rev-5/final">NIST SP 800-53 Rev 5</a>
 * @since 1.0.0
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configures the HTTP security filter chain with production-grade
     * security headers and policies.
     *
     * @param http the {@link HttpSecurity} builder
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                // ── CORS (for React frontend) ────────────────────────────
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                // ── Session management (NIST IA-2) ───────────────────────
                // Stateless API — no server-side sessions
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ── CSRF (disabled for stateless REST API) ───────────────
                // Mitigated by token-based auth (JWT/OAuth2) in production
                .csrf(AbstractHttpConfigurer::disable)

                // ── Security headers ─────────────────────────────────────
                .headers(headers -> headers
                        // HSTS: 1 year, include subdomains, preload-ready (NIST SC-8)
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .preload(true)
                                .maxAgeInSeconds(31_536_000))

                        // X-Content-Type-Options: nosniff (prevents MIME-sniffing)
                        .contentTypeOptions(contentType -> {
                        })

                        // X-Frame-Options: DENY (prevents clickjacking)
                        .frameOptions(frame -> frame.deny())

                        // X-XSS-Protection: 0 (modern CSP supersedes this legacy header)
                        .xssProtection(xss ->
                                xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.DISABLED))

                        // Referrer-Policy (SOC 2 CC6.1 — limit info leakage)
                        .referrerPolicy(referrer ->
                                referrer.policy(ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))

                        // Content-Security-Policy (NIST SC-18 — restrict resource loading)
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(
                                        "default-src 'self'; " +
                                        "script-src 'self'; " +
                                        "style-src 'self' 'unsafe-inline'; " +
                                        "img-src 'self' data:; " +
                                        "font-src 'self'; " +
                                        "connect-src 'self'; " +
                                        "frame-ancestors 'none'; " +
                                        "form-action 'self'; " +
                                        "base-uri 'self'"))

                        // Permissions-Policy (SOC 2 CC6.6 — disable unused browser features)
                        .permissionsPolicy(permissions -> permissions
                                .policy("geolocation=(), camera=(), microphone=(), " +
                                        "payment=(), usb=(), magnetometer=()"))
                        // Cache-Control: no-cache, no-store, max-age=0 (enabled by default
                        // in Spring Security — NIST SC-28, prevents caching sensitive data)
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
                        // API endpoints — permit for POC (add JWT/OAuth2 in production)
                        .requestMatchers("/api/**").permitAll()
                        // All other requests require authentication
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}



