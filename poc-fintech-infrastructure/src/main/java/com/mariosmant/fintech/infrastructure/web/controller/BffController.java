package com.mariosmant.fintech.infrastructure.web.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Backend-for-Frontend (BFF) endpoints.
 *
 * <p>Exposes the SPA-facing surface needed for a session-cookie-based security model:</p>
 * <ul>
 *   <li>{@code GET /bff/user} — returns the authenticated principal's non-sensitive
 *       identity projection (subject, username, roles, admin flag). The SPA uses this
 *       to drive UI without ever needing access to a raw token.</li>
 *   <li>{@code POST /bff/logout} — server-side logout: invalidates the HTTP session,
 *       clears {@code __Host-SESSION} and {@code __Host-XSRF-TOKEN} cookies, returns 204.</li>
 *   <li>{@code GET /bff/public/csrf} — issues a fresh {@code __Host-XSRF-TOKEN} cookie
 *       (no-op when CSRF is disabled) so the SPA can bootstrap the double-submit token.</li>
 * </ul>
 *
 * <p><b>Why a BFF layer even in JWT-Bearer mode?</b> The {@code /bff/user} endpoint
 * decouples the SPA from Keycloak-specific claim shapes (realm_access / resource_access)
 * and removes the need for the SPA to decode the JWT itself — a prerequisite for the
 * upcoming migration to server-held tokens where the token never
 * leaves the backend.</p>
 *
 * <p>Authorization: both {@code /bff/user} and {@code /bff/logout} require authentication
 * (enforced at the filter-chain level, see {@code SecurityConfig}). {@code /bff/public/**}
 * is reachable unauthenticated — currently only {@code /bff/public/csrf}.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@RestController
@RequestMapping(path = "/bff", produces = MediaType.APPLICATION_JSON_VALUE)
public class BffController {

    /**
     * Returns the identity projection of the current principal.
     *
     * <p>Shape (stable contract for the SPA):</p>
     * <pre>{@code
     * {
     *   "authenticated": true,
     *   "subject": "<uuid>",         // JWT sub
     *   "username": "alice",          // preferred_username
     *   "name": "Alice Anderson",    // name (optional)
     *   "email": "alice@example.com", // email (optional)
     *   "roles": ["USER"],            // ROLE_ prefix stripped
     *   "admin": false
     * }
     * }</pre>
     */
    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> user() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("authenticated", false));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authenticated", true);

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            body.put("subject", Objects.toString(jwt.getSubject(), ""));
            body.put("username", Objects.toString(jwt.getClaimAsString("preferred_username"), ""));
            Optional.ofNullable(jwt.getClaimAsString("name"))
                    .ifPresent(v -> body.put("name", v));
            Optional.ofNullable(jwt.getClaimAsString("email"))
                    .ifPresent(v -> body.put("email", v));
        } else if (auth.getPrincipal() instanceof OidcUser oidc) {
            // BFF: session-backed OAuth2 Login (tokens held server-side).
            body.put("subject", Objects.toString(oidc.getSubject(), ""));
            body.put("username", Objects.toString(oidc.getPreferredUsername(), oidc.getName()));
            Optional.ofNullable(oidc.getFullName()).ifPresent(v -> body.put("name", v));
            Optional.ofNullable(oidc.getEmail()).ifPresent(v -> body.put("email", v));
        } else if (auth.getPrincipal() instanceof OAuth2User o2) {
            body.put("subject", Objects.toString(o2.getAttribute("sub"), auth.getName()));
            body.put("username", Objects.toString(o2.getAttribute("preferred_username"), auth.getName()));
            Optional.ofNullable((String) o2.getAttribute("name")).ifPresent(v -> body.put("name", v));
            Optional.ofNullable((String) o2.getAttribute("email")).ifPresent(v -> body.put("email", v));
        } else {
            body.put("subject", auth.getName());
            body.put("username", auth.getName());
        }

        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .toList();
        body.put("roles", roles);
        body.put("admin", roles.contains("ADMIN"));

        return ResponseEntity.ok(body);
    }

    /**
     * Server-side logout: invalidates the session and clears auth cookies.
     *
     * <p>In JWT-Bearer mode this has no session to invalidate; the cookie-clear
     * operation is still performed to purge any stale {@code __Host-*} cookies
     * the browser may hold.</p>
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        // Purge both __Host- cookies (must match original attributes to be deleted).
        clearHostCookie(response, "__Host-SESSION");
        clearHostCookie(response, "__Host-XSRF-TOKEN");

        return ResponseEntity.noContent().build();
    }

    /**
     * Issues a fresh CSRF cookie. No-op when CSRF is disabled at the filter chain
     * level (the attribute is simply absent from the request).
     */
    @GetMapping("/public/csrf")
    public ResponseEntity<Map<String, Object>> csrf(HttpServletRequest request) {
        Object attr = request.getAttribute(CsrfToken.class.getName());
        if (attr instanceof CsrfToken token) {
            // Touching token.getToken() ensures the cookie is issued by the repository.
            return ResponseEntity.ok(Map.of(
                    "headerName", token.getHeaderName(),
                    "parameterName", token.getParameterName(),
                    "token", token.getToken()
            ));
        }
        return ResponseEntity.ok(Map.of("enabled", false));
    }

    /** Clears a {@code __Host-}-prefixed cookie by setting {@code Max-Age=0}. */
    private static void clearHostCookie(HttpServletResponse response, String name) {
        Cookie c = new Cookie(name, "");
        c.setPath("/");
        c.setHttpOnly(true);
        c.setSecure(true);
        c.setMaxAge(0);
        c.setAttribute("SameSite", "Strict");
        response.addCookie(c);
    }
}

