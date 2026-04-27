package com.mariosmant.fintech.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Utility for extracting authenticated user information from the Security Context.
 * All user identity MUST come from the server-validated JWT — never from client input.
 *
 * <p>NIST IA-2: Identification and Authentication (Organizational Users)</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public final class SecurityContextUtil {

    private SecurityContextUtil() { /* utility */ }

    /**
     * Returns the authenticated user's Keycloak subject ID (UUID).
     *
     * <p>Supports both auth modes:</p>
     * <ul>
     *   <li>Resource-Server (default) — principal is {@link JwtAuthenticationToken};
     *       {@code sub} comes from the validated access token.</li>
     *   <li>BFF (profile {@code bff}) — principal is {@link OAuth2AuthenticationToken}
     *       wrapping an {@link OidcUser}; {@code sub} comes from the ID-token claims.</li>
     * </ul>
     *
     * @return the user ID from the OIDC {@code sub} claim
     * @throws IllegalStateException if no authenticated user in context
     */
    public static String getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getSubject();
        }
        if (auth instanceof OAuth2AuthenticationToken oauth2Auth) {
            OAuth2User principal = oauth2Auth.getPrincipal();
            if (principal instanceof OidcUser oidc && oidc.getSubject() != null) {
                return oidc.getSubject();
            }
            Object sub = principal != null ? principal.getAttributes().get("sub") : null;
            if (sub != null) {
                return sub.toString();
            }
            // Last-resort fallback so the controller can still serve the
            // authenticated user even if the IdP omitted the sub claim.
            return oauth2Auth.getName();
        }
        throw new IllegalStateException("No authenticated user in security context");
    }

    /**
     * Returns the authenticated user's preferred username from Keycloak.
     *
     * @return the preferred_username claim value, or "unknown"
     */
    public static String getAuthenticatedUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String username = jwt.getClaimAsString("preferred_username");
            return username != null ? username : "unknown";
        }
        if (auth instanceof OAuth2AuthenticationToken oauth2Auth) {
            OAuth2User principal = oauth2Auth.getPrincipal();
            if (principal != null) {
                Object username = principal.getAttributes().get("preferred_username");
                if (username != null) {
                    return username.toString();
                }
            }
            return oauth2Auth.getName() != null ? oauth2Auth.getName() : "unknown";
        }
        return "unknown";
    }

    /**
     * Returns the JWT token string for the authenticated user.
     *
     * @return the raw JWT token value
     */
    public static String getJwtTokenValue() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getTokenValue();
        }
        throw new IllegalStateException("No JWT token in security context");
    }

    /**
     * Returns {@code true} if the caller has the {@code ROLE_ADMIN} authority,
     * as mapped by {@code KeycloakJwtAuthoritiesConverter} from the Keycloak
     * realm role {@code admin}. Used by read endpoints to decide whether the
     * caller is allowed to observe data owned by other users.
     *
     * <p>Does <b>not</b> short-circuit any {@code @PreAuthorize} check — it is a
     * complement, not a substitute, and must always be combined with an explicit
     * per-resource authorization decision.</p>
     */
    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}

