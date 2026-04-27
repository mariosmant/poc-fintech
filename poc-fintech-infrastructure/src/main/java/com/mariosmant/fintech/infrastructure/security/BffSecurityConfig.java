package com.mariosmant.fintech.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.cors.CorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * BFF security filter chain — activated by {@code SPRING_PROFILES_ACTIVE=bff}.
 *
 * <p>Replaces the stateless JWT Resource Server flow with a server-side
 * Authorization Code + PKCE OAuth2 Login. After successful login the backend
 * holds the access/refresh token in the {@code HttpSession}; the browser only
 * ever sees the {@code __Host-SESSION} cookie. This mitigates XSS token-theft
 * (NIST SP 800-63B §5.2.10) and removes the need for the SPA to touch OAuth
 * state at all (IETF {@code draft-ietf-oauth-browser-based-apps}).</p>
 *
 * <h3>Behaviour differences vs. {@link SecurityConfig}</h3>
 * <ul>
 *   <li>Session policy: {@code IF_REQUIRED} (cookie-backed) instead of {@code STATELESS}.</li>
 *   <li>CSRF: mandatory, {@code __Host-XSRF-TOKEN} double-submit cookie.</li>
 *   <li>Authentication: {@code .oauth2Login()} + {@code .oidcLogout()} RP-initiated logout.</li>
 *   <li>Unauthenticated {@code /api/**} + {@code /bff/**} return HTTP 401 (JSON-friendly) —
 *       the SPA triggers login by navigating to {@code /oauth2/authorization/keycloak},
 *       which bypasses fetch's opaque-redirect problem.</li>
 *   <li>Role mapping: realm roles from the OIDC {@code realm_access.roles} claim are
 *       converted to {@code ROLE_*} authorities so {@code @PreAuthorize("hasRole('ADMIN')")}
 *       behaves identically to the resource-server path.</li>
 * </ul>
 *
 * @author mariosmant
 * @see SecurityConfig
 * @see BFF + __Host-cookie hardening
 * @since 1.0.0
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("bff")
public class BffSecurityConfig {

    /**
     * Session-backed OAuth2 Login filter chain. CSRF is always on in this mode.
     */
    @Bean
    public SecurityFilterChain bffSecurityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            ClientRegistrationRepository clientRegistrationRepository) throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                // ── Session (cookie-backed, __Host-SESSION) ──────────────
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

                // ── CSRF (mandatory in BFF mode) ────────────────────────
                .csrf(csrf -> {
                    CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
                    repo.setCookieName("__Host-XSRF-TOKEN");
                    repo.setCookieCustomizer(c -> c
                            .secure(true).path("/").sameSite("Strict"));
                    CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
                    handler.setCsrfRequestAttributeName(null); // SPA-friendly (no deferred token)
                    csrf.csrfTokenRepository(repo).csrfTokenRequestHandler(handler);
                });

        // ── Hardened response headers ─────────────
        SecurityHeaders.apply(http);

        http
                // ── OAuth2 Login: backend performs the code flow ─────────
                .oauth2Login(login -> login
                        .userInfoEndpoint(u -> u.oidcUserService(oidcUserService()))
                        // After successful code exchange, ALWAYS land the user on
                        // the SPA root ("/"), not on whatever /bff/* or /api/*
                        // request first triggered the 401. SavedRequest replay
                        // would otherwise drop the browser on a JSON endpoint
                        // (e.g. /bff/user?continue) instead of the SPA.
                        .successHandler(bffLoginSuccessHandler())
                        // Default authorization endpoint: /oauth2/authorization/{registrationId}
                        // Default redirection endpoint: /login/oauth2/code/{registrationId}
                )
                // ── No request caching — fetch-driven 401s must NOT save a
                //    request that would later replay through ?continue and
                //    pull the browser back to a JSON endpoint. Combined with
                //    the explicit successHandler above, this guarantees the
                //    SPA is always reached after login. ────────────────────
                .requestCache(rc -> rc.requestCache(new NullRequestCache()))
                // ── RP-initiated logout: propagates to Keycloak ─────────
                .logout(logout -> logout
                        .logoutUrl("/bff/logout")
                        .logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository))
                        .deleteCookies("__Host-SESSION", "__Host-XSRF-TOKEN")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true))

                // ── Global 401 entry point for XHR/fetch clients ─────────
                //     Browsers cannot follow a 302 to a cross-origin IdP from
                //     fetch (opaque redirect), so the BFF returns 401 JSON and
                //     the SPA performs a top-level navigation to
                //     /oauth2/authorization/keycloak to trigger the code flow. ─
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

                // ── Authorization rules ─────────────────────────────────
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health", "/actuator/health/**", "/actuator/info",
                                "/actuator/prometheus",
                                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**"
                        ).permitAll()
                        // Audit-chain verifier — admin only (PCI DSS §10.5, NIST AU-9(3))
                        .requestMatchers("/actuator/auditchain", "/actuator/auditchain/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/bff/public/**").permitAll()
                        // OAuth2 client endpoints must be reachable unauthenticated
                        .requestMatchers("/oauth2/authorization/**", "/login/oauth2/code/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers("/bff/**").authenticated()
                        .anyRequest().authenticated());

        return http.build();
    }

    /**
     * Maps Keycloak realm/client roles into Spring Security {@code ROLE_*} authorities.
     * Reads claims from (in order): the ID token + UserInfo (already merged in
     * {@link OidcUser#getClaims()}), the OAuth2 access token (Keycloak issues
     * a JWT — parsed defensively), and flat {@code roles}/{@code groups}
     * claims. Keeps method security ({@code @PreAuthorize("hasRole(...)")})
     * consistent with the resource-server JWT path even if the realm export
     * forgets to set {@code id.token.claim} / {@code userinfo.token.claim}
     * on the role mappers.
     */
    @Bean
    public OidcUserService oidcUserService() {
        OidcUserService delegate = new OidcUserService();
        return new OidcUserService() {
            @Override
            public OidcUser loadUser(org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest req) {
                OidcUser base = delegate.loadUser(req);
                Set<GrantedAuthority> authorities = new HashSet<>(base.getAuthorities());

                // (1) Claims merged from ID token + UserInfo response.
                addRolesFromClaims(base.getClaims(), authorities);

                // (2) Fallback: Keycloak access tokens are JWTs — decode the
                //     payload and harvest realm/client roles from there if
                //     the ID token / UserInfo did not carry them.
                String accessToken = req.getAccessToken() != null ? req.getAccessToken().getTokenValue() : null;
                Map<String, Object> atClaims = decodeJwtPayload(accessToken);
                if (atClaims != null) {
                    addRolesFromClaims(atClaims, authorities);
                }

                return new DefaultOidcUser(authorities, base.getIdToken(), base.getUserInfo(), "preferred_username");
            }
        };
    }

    /**
     * Extracts roles from the standard Keycloak claim shapes:
     * {@code realm_access.roles}, {@code resource_access.<client>.roles},
     * top-level {@code roles}, and Keycloak {@code groups} (with the
     * leading {@code /} stripped).
     */
    @SuppressWarnings("unchecked")
    private static void addRolesFromClaims(Map<String, Object> claims, Set<GrantedAuthority> authorities) {
        if (claims == null || claims.isEmpty()) {
            return;
        }
        // realm_access.roles
        Object realmAccess = claims.get("realm_access");
        if (realmAccess instanceof Map<?, ?> map) {
            addRoleList(map.get("roles"), authorities);
        }
        // resource_access.<client>.roles  (each client bucket)
        Object resourceAccess = claims.get("resource_access");
        if (resourceAccess instanceof Map<?, ?> rmap) {
            for (Object bucket : rmap.values()) {
                if (bucket instanceof Map<?, ?> bmap) {
                    addRoleList(bmap.get("roles"), authorities);
                }
            }
        }
        // flat "roles" claim
        addRoleList(claims.get("roles"), authorities);
        // "groups" claim — strip leading "/"
        Object groups = claims.get("groups");
        if (groups instanceof List<?> list) {
            for (Object g : list) {
                if (g instanceof String s && !s.isBlank()) {
                    String role = s.startsWith("/") ? s.substring(1) : s;
                    if (!role.isBlank()) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
                    }
                }
            }
        }
    }

    private static void addRoleList(Object roles, Set<GrantedAuthority> authorities) {
        if (roles instanceof List<?> list) {
            for (Object r : list) {
                if (r != null) {
                    String s = r.toString();
                    if (!s.isBlank()) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + s.toUpperCase()));
                    }
                }
            }
        }
    }

    /**
     * Decodes the payload of a JWT (header.payload.signature) without
     * verifying the signature — verification was already performed by the
     * OAuth2 client during the code exchange. Returns {@code null} if the
     * value is not a parseable JWT (e.g. an opaque access token).
     */
    private static Map<String, Object> decodeJwtPayload(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(new String(payload, StandardCharsets.UTF_8));
            if (node == null || !node.isObject()) {
                return null;
            }
            return mapper.convertValue(node, Map.class);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * RP-initiated logout success handler — redirects to Keycloak's end_session_endpoint
     * which terminates the SSO session, then bounces back to the SPA origin.
     */
    @Bean
    public OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler(
            ClientRegistrationRepository clientRegistrationRepository) {
        OidcClientInitiatedLogoutSuccessHandler handler =
                new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        handler.setPostLogoutRedirectUri("{baseUrl}/");
        return handler;
    }

    /**
     * OAuth2-Login success handler — always redirects to the SPA root.
     *
     * <p>The default {@code SavedRequestAwareAuthenticationSuccessHandler} would
     * replay the original protected request that triggered the 401 (e.g.
     * {@code /bff/user}), but in BFF mode that request is the JSON-returning
     * identity endpoint, not the SPA shell. We unconditionally land the user
     * on {@code "/"} instead — the SPA then re-runs {@code fetchBffUser()},
     * sees an authenticated session, and renders the dashboard.</p>
     */
    @Bean
    public SimpleUrlAuthenticationSuccessHandler bffLoginSuccessHandler() {
        SimpleUrlAuthenticationSuccessHandler handler = new SimpleUrlAuthenticationSuccessHandler("/");
        handler.setAlwaysUseDefaultTargetUrl(true);
        return handler;
    }
}







