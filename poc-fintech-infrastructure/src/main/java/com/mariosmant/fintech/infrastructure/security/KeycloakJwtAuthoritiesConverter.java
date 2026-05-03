package com.mariosmant.fintech.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds a {@link Converter} that extracts Keycloak roles from a validated JWT
 * and produces Spring Security authorities prefixed with {@code ROLE_}.
 *
 * <p>Reads from two standard Keycloak claim paths:
 * <ul>
 *   <li>{@code realm_access.roles} — realm-level roles (e.g. {@code user}, {@code admin}).</li>
 *   <li>{@code resource_access.<client>.roles} — client-scoped roles for
 *       {@link #CLIENT_ID} (configured as the OAuth2 public client id).</li>
 * </ul>
 *
 * <p>Extracted outside of {@code SecurityConfig} so the <b>production</b> and
 * <b>test</b> filter chains share the same authority-mapping contract. Without
 * this, integration tests would authenticate but receive only scope-derived
 * authorities, making {@code @PreAuthorize("hasRole('USER')")} asymmetric
 * between main and test profiles.</p>
 *
 * @since 1.0.0
 */
public final class KeycloakJwtAuthoritiesConverter {

    private static final Logger log = LoggerFactory.getLogger(KeycloakJwtAuthoritiesConverter.class);

    /**
     * Keycloak client id whose {@code resource_access.<client>.roles} should be mapped.
     * Matches the realm-JSON client id used by the React SPA.
     */
    public static final String CLIENT_ID = "poc-fintech-spa";

    private KeycloakJwtAuthoritiesConverter() { /* utility */ }

    /**
     * Returns a fully-assembled {@link JwtAuthenticationConverter} ready to be plugged
     * into {@code oauth2ResourceServer().jwt().jwtAuthenticationConverter(...)}.
     */
    public static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter conv = new JwtAuthenticationConverter();
        conv.setJwtGrantedAuthoritiesConverter(authoritiesConverter());
        return conv;
    }

    /** Returns the low-level authorities converter — useful for composition in tests. */
    public static Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter() {
        return jwt -> {
            Stream<String> realmRoles = Optional.ofNullable(jwt.getClaimAsMap("realm_access"))
                    .map(ra -> ra.get("roles"))
                    .filter(List.class::isInstance)
                    .map(roles -> ((List<?>) roles).stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast))
                    .orElse(Stream.empty());

            Stream<String> clientRoles = Optional.ofNullable(jwt.getClaimAsMap("resource_access"))
                    .map(ra -> ra.get(CLIENT_ID))
                    .filter(Map.class::isInstance)
                    .map(client -> ((Map<?, ?>) client).get("roles"))
                    .filter(List.class::isInstance)
                    .map(roles -> ((List<?>) roles).stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast))
                    .orElse(Stream.empty());

            // Defensive: some Keycloak deployments flatten roles via a custom mapper to a
            // top-level "roles" claim, or surface them as Keycloak groups (leading-slash stripped).
            // Including these makes the converter resilient to common realm-export drift.
            Stream<String> flatRoles = Optional.ofNullable(jwt.getClaim("roles"))
                    .filter(List.class::isInstance)
                    .map(roles -> ((List<?>) roles).stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast))
                    .orElse(Stream.empty());

            Stream<String> groupRoles = Optional.ofNullable(jwt.getClaim("groups"))
                    .filter(List.class::isInstance)
                    .map(groups -> ((List<?>) groups).stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast)
                            .map(g -> g.startsWith("/") ? g.substring(1) : g))
                    .orElse(Stream.empty());

            Set<GrantedAuthority> authorities = Stream.of(realmRoles, clientRoles, flatRoles, groupRoles)
                    .flatMap(s -> s)
                    .filter(r -> r != null && !r.isBlank())
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                    .collect(Collectors.toSet());

            if (authorities.isEmpty() && log.isDebugEnabled()) {
                // No PII: only claim names + subject. Helps diagnose realm/scope misconfiguration
                // when an otherwise-valid JWT yields a 403 from method security.
                log.debug("JWT for sub={} produced no role authorities; available claims: {}",
                        jwt.getSubject(), jwt.getClaims().keySet());
            }
            return authorities;
        };
    }
}

