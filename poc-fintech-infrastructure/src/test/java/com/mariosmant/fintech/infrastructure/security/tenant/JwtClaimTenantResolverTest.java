package com.mariosmant.fintech.infrastructure.security.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * verifies the JWT-claim tenant resolver picks the
 * canonical claim, falls back gracefully, sanitises hostile values, and
 * never returns {@code null} or a key-space-injection vector.
 */
class JwtClaimTenantResolverTest {

    private final JwtClaimTenantResolver resolver = new JwtClaimTenantResolver();
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Anonymous request → SHARED tenant")
    void anonymousReturnsShared() {
        assertThat(resolver.resolveTenant(request)).isEqualTo(TenantResolver.SHARED_TENANT);
    }

    @Test
    @DisplayName("Non-JWT authentication → SHARED tenant")
    void nonJwtAuthReturnsShared() {
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new AnonymousAuthenticationToken(
                "k", "anon", List.of(new SimpleGrantedAuthority("ROLE_ANON"))));
        SecurityContextHolder.setContext(ctx);
        assertThat(resolver.resolveTenant(request)).isEqualTo(TenantResolver.SHARED_TENANT);
    }

    @Test
    @DisplayName("tenant_id claim wins over tid and azp")
    void tenantIdClaimWins() {
        authenticateWith(Map.of(
                "tenant_id", "Acme-Bank",
                "tid",       "should-not-win",
                "azp",       "also-not"));
        assertThat(resolver.resolveTenant(request)).isEqualTo("acme-bank");
    }

    @Test
    @DisplayName("Falls back to tid when tenant_id missing")
    void fallsBackToTid() {
        authenticateWith(Map.of("tid", "00000000-1111-2222-3333-444444444444"));
        assertThat(resolver.resolveTenant(request))
                .isEqualTo("00000000-1111-2222-3333-444444444444");
    }

    @Test
    @DisplayName("Falls back to azp when tenant_id and tid missing")
    void fallsBackToAzp() {
        authenticateWith(Map.of("azp", "poc-fintech-spa"));
        assertThat(resolver.resolveTenant(request)).isEqualTo("poc-fintech-spa");
    }

    @Test
    @DisplayName("Hostile claim with key-space delimiter is rejected → SHARED")
    void hostileClaimRejected() {
        authenticateWith(Map.of("tenant_id", "evil:user:victim"));
        assertThat(resolver.resolveTenant(request)).isEqualTo(TenantResolver.SHARED_TENANT);
    }

    @Test
    @DisplayName("Whitespace-only / over-long claims fall back to next claim")
    void blankAndOversizedFallThrough() {
        authenticateWith(Map.of(
                "tenant_id", "   ",
                "tid",       "x".repeat(65),
                "azp",       "valid-fallback"));
        assertThat(resolver.resolveTenant(request)).isEqualTo("valid-fallback");
    }

    private static void authenticateWith(Map<String, Object> claims) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .subject("user-123")
                .claims(c -> c.putAll(claims))
                .build();
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new JwtAuthenticationToken(jwt));
        SecurityContextHolder.setContext(ctx);
    }
}

