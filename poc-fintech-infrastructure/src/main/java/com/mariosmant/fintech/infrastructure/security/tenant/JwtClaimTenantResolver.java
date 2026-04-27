package com.mariosmant.fintech.infrastructure.security.tenant;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * JWT-claim-based {@link TenantResolver}.
 *
 * <p>Lookup order:</p>
 * <ol>
 *   <li>{@code tenant_id} claim — the canonical multi-tenancy claim a
 *       Keycloak {@code attribute mapper} can be configured to emit.</li>
 *   <li>{@code tid} claim — Microsoft Entra / Azure AD's tenant claim, kept
 *       as a fallback so the same code works against either IdP without a
 *       configuration change.</li>
 *   <li>{@code azp} claim — last-resort fallback so multi-client deployments
 *       without an explicit tenant claim still get isolation across clients.</li>
 *   <li>{@link TenantResolver#SHARED_TENANT} — for unauthenticated requests
 *       or tokens missing all of the above.</li>
 * </ol>
 *
 * <p>The resolved value is normalised: lower-case, trimmed, and matched
 * against {@link #SAFE_TENANT_PATTERN}. Anything that fails validation is
 * logged at {@code WARN} and replaced with {@link TenantResolver#SHARED_TENANT}
 * — never propagated into the rate-limit key (defence against
 * key-space-injection via crafted claims, OWASP Top-10 A03 / A05).</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public class JwtClaimTenantResolver implements TenantResolver {

    private static final Logger log = LoggerFactory.getLogger(JwtClaimTenantResolver.class);

    /**
     * Allowed tenant-id shape: lower-case alphanumerics, {@code .}, {@code -},
     * {@code _}, max 64 chars. Anything else is rejected.
     */
    static final Pattern SAFE_TENANT_PATTERN = Pattern.compile("[a-z0-9._-]{1,64}");

    private static final String[] CLAIM_LOOKUP_ORDER = { "tenant_id", "tid", "azp" };

    @Override
    public String resolveTenant(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            return SHARED_TENANT;
        }
        Jwt token = jwtAuth.getToken();
        for (String claim : CLAIM_LOOKUP_ORDER) {
            String raw = token.getClaimAsString(claim);
            String safe = sanitise(raw);
            if (safe != null) {
                return safe;
            }
            if (raw != null && !raw.isBlank()) {
                // Non-null but failed the safety check — log once and keep
                // looking down the fallback chain.
                log.warn("Tenant claim '{}' was present but failed safety check; "
                        + "ignoring and falling back. value-length={}", claim, raw.length());
            }
        }
        return SHARED_TENANT;
    }

    private static String sanitise(String raw) {
        if (raw == null) return null;
        String normalised = raw.trim().toLowerCase(Locale.ROOT);
        if (normalised.isEmpty()) return null;
        if (!SAFE_TENANT_PATTERN.matcher(normalised).matches()) return null;
        return normalised;
    }
}

