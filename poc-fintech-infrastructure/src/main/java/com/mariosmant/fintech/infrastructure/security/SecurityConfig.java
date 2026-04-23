package com.mariosmant.fintech.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.CrossOriginEmbedderPolicyHeaderWriter;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.web.cors.CorsConfigurationSource;


/**
 * Security configuration aligned with NIST SP 800-53 Rev 5, NIST SP 800-63B (AAL2),
 * SOG-IS Crypto Evaluation Scheme, and SOC 2 Type II Trust Services Criteria
 * (CC6.1 Logical Access, CC6.6 Transmission, CC7.2 Monitoring, CC8.1 Change Mgmt).
 *
 * <p>Integrates Keycloak as OAuth2/OIDC identity provider via JWT resource server.
 * All API endpoints require authentication. Actuator health/info and Swagger are public.</p>
 *
 * <p><b>Cookie policy (hardened, BFF-ready):</b> the servlet session cookie is configured
 * in {@code application.yml} as {@code HttpOnly} + {@code Secure} + {@code SameSite=Strict}.
 * The cookie name {@code __Host-SESSION} (set via {@code server.servlet.session.cookie.name})
 * binds the cookie to the exact origin per the RFC 6265bis "__Host-" prefix contract
 * (requires {@code Secure}, {@code Path=/}, and <b>no</b> {@code Domain} attribute).</p>
 *
 * <p><b>CSRF:</b> enabled for the BFF profile (cookie-based session auth) via a
 * double-submit {@code __Host-XSRF-TOKEN} cookie. Disabled in the default JWT-Bearer
 * profile — Bearer tokens are immune to CSRF because attackers cannot read them to
 * forge the {@code Authorization} header (same-origin policy).</p>
 *
 * <p><b>Applied response headers:</b></p>
 * <table>
 *   <tr><th>Header / Policy</th><th>Standard</th><th>Rationale</th></tr>
 *   <tr><td>HSTS (1 year, includeSubDomains, preload)</td><td>NIST SC-8 / SOC 2 CC6.6</td>
 *       <td>Enforces TLS for all connections</td></tr>
 *   <tr><td>X-Content-Type-Options: nosniff</td><td>OWASP ASVS V14.4.4</td>
 *       <td>Prevents MIME-type sniffing attacks</td></tr>
 *   <tr><td>X-Frame-Options: DENY</td><td>OWASP ASVS V14.4.7</td>
 *       <td>Prevents clickjacking (framing)</td></tr>
 *   <tr><td>Content-Security-Policy (strict, no unsafe-inline/eval scripts)</td><td>NIST SC-18 / OWASP A03</td>
 *       <td>Defence-in-depth against XSS / injection</td></tr>
 *   <tr><td>Cross-Origin-Opener-Policy: same-origin</td><td>Fetch Metadata / Spectre mitigation</td>
 *       <td>Isolates browsing context from cross-origin windows</td></tr>
 *   <tr><td>Cross-Origin-Embedder-Policy: require-corp</td><td>Fetch Metadata</td>
 *       <td>Blocks cross-origin resources without explicit CORP opt-in</td></tr>
 *   <tr><td>Cross-Origin-Resource-Policy: same-origin</td><td>Fetch Metadata</td>
 *       <td>Prevents cross-origin resource theft / side-channel reads</td></tr>
 *   <tr><td>Referrer-Policy: strict-origin-when-cross-origin</td><td>SOC 2 CC6.1</td>
 *       <td>Limits referrer information leakage</td></tr>
 *   <tr><td>Permissions-Policy (browser features disabled)</td><td>SOC 2 CC6.6</td>
 *       <td>Disables unnecessary browser APIs</td></tr>
 *   <tr><td>Cache-Control: no-store (on error paths)</td><td>NIST SC-28</td>
 *       <td>Prevents caching of sensitive financial data</td></tr>
 *   <tr><td>Stateless sessions + OAuth2 JWT (default profile)</td><td>NIST IA-2</td>
 *       <td>No server-side session state in Bearer mode</td></tr>
 *   <tr><td>Session-backed OAuth2 Login (bff profile)</td><td>NIST IA-2 / IA-5</td>
 *       <td>Tokens never reach the browser — mitigates XSS token theft</td></tr>
 * </table>
 *
 * @author mariosmant
 * @see <a href="https://csrc.nist.gov/publications/detail/sp/800-53/rev-5/final">NIST SP 800-53 Rev 5</a>
 * @see <a href="https://csrc.nist.gov/publications/detail/sp/800-63b/final">NIST SP 800-63B (AAL2)</a>
 * @see <a href="https://www.sogis.eu/">SOG-IS Crypto Evaluation Scheme</a>
 * @since 1.0.0
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("!test")
public class SecurityConfig {

    /**
     * When {@code true} (typically under the {@code bff} profile), enables CSRF
     * protection backed by a {@code __Host-XSRF-TOKEN} cookie (double-submit pattern)
     * for session-authenticated requests. Defaults to {@code false} — the JWT Bearer
     * flow does not require CSRF (no ambient credentials).
     */
    @Value("${app.security.csrf.enabled:false}")
    private boolean csrfEnabled;

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
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // ── CSRF (double-submit cookie, BFF mode only) ───────────────────
        if (csrfEnabled) {
            // Cookie is issued with HttpOnly=false so the SPA can read it and
            // echo it in the X-XSRF-TOKEN header (double-submit).
            // `__Host-XSRF-TOKEN` requires Secure + Path=/ + no Domain (RFC 6265bis).
            CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
            csrfRepo.setCookieName("__Host-XSRF-TOKEN");
            csrfRepo.setCookieCustomizer(c -> c
                    .secure(true)
                    .path("/")
                    .sameSite("Strict"));
            CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
            csrfHandler.setCsrfRequestAttributeName(null); // opt out of deferred token (SPA-friendly)
            http.csrf(csrf -> csrf
                    .csrfTokenRepository(csrfRepo)
                    .csrfTokenRequestHandler(csrfHandler));
        } else {
            http.csrf(AbstractHttpConfigurer::disable);
        }

        http
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
                        // Cross-Origin isolation (Spectre / side-channel mitigation)
                        .crossOriginOpenerPolicy(coop -> coop.policy(
                                CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy.SAME_ORIGIN))
                        .crossOriginEmbedderPolicy(coep -> coep.policy(
                                CrossOriginEmbedderPolicyHeaderWriter.CrossOriginEmbedderPolicy.REQUIRE_CORP))
                        .crossOriginResourcePolicy(corp -> corp.policy(
                                CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy.SAME_ORIGIN))
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(
                                        "default-src 'self'; " +
                                        "script-src 'self'; " +
                                        "script-src-attr 'none'; " +
                                        "style-src 'self' 'unsafe-inline'; " +
                                        "img-src 'self' data:; " +
                                        "font-src 'self'; " +
                                        "connect-src 'self'; " +
                                        "frame-ancestors 'none'; " +
                                        "form-action 'self'; " +
                                        "base-uri 'self'; " +
                                        "object-src 'none'; " +
                                        "upgrade-insecure-requests"))
                        .permissionsPolicy(permissions -> permissions
                                .policy("accelerometer=(), autoplay=(), browsing-topics=(), " +
                                        "camera=(), cross-origin-isolated=(), display-capture=(), " +
                                        "encrypted-media=(), fullscreen=(), geolocation=(), " +
                                        "gyroscope=(), hid=(), identity-credentials-get=(), " +
                                        "idle-detection=(), magnetometer=(), microphone=(), " +
                                        "midi=(), payment=(), picture-in-picture=(), " +
                                        "publickey-credentials-create=(), publickey-credentials-get=(), " +
                                        "screen-wake-lock=(), serial=(), storage-access=(), " +
                                        "usb=(), web-share=(), xr-spatial-tracking=()"))
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
                        // BFF user/session endpoints (session-authenticated; may return 401 JSON)
                        .requestMatchers("/bff/public/**").permitAll()
                        // All API endpoints require authentication (hostile environment)
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers("/bff/**").authenticated()
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
