package com.mariosmant.fintech.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
     * @return the user ID from JWT {@code sub} claim
     * @throws IllegalStateException if no authenticated user in context
     */
    public static String getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getSubject();
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
}

