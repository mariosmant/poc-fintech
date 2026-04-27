package com.mariosmant.fintech.infrastructure.security.tenant;

import jakarta.servlet.http.HttpServletRequest;

/**
 * derives a tenant identifier for the current
 * request, used to namespace the rate-limit key
 * ({@code tenant:<id>:user:<sub>} / {@code tenant:<id>:ip:<addr>}).
 *
 * <p>The port is intentionally minimal: a single resolver call per request,
 * no exceptions thrown, deterministic fallback to the "shared" tenant for
 * unauthenticated requests or tokens missing the claim. Implementations
 * MUST be cheap (the call sits on every {@code /api/**} request hot path).</p>
 *
 * <h2>Why a tenant prefix?</h2>
 * <p>Without a tenant namespace, two tenants whose users happen to share a
 * {@code sub} (rare but possible across IdPs) would share a bucket. The
 * prefix also makes it trivial to operate per-tenant rate-limit dashboards
 * and to wipe a single tenant's state without flushing the whole keyspace.</p>
 *
 * <h2>Standards mapping</h2>
 * <ul>
 *   <li>NIST SP 800-53 SC-2 — Security Function Isolation (per-tenant boundaries).</li>
 *   <li>OWASP ASVS V11.1 — multi-tenant resource separation.</li>
 *   <li>SOC 2 CC6.1 — logical access control.</li>
 * </ul>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public interface TenantResolver {

    /**
     * Sentinel returned when no tenant can be derived. The single-tenant /
     * legacy deployment behaves as if every request belongs to this tenant,
     * preserving keyspace shape ({@code tenant:shared:user:<sub>}).
     */
    String SHARED_TENANT = "shared";

    /**
     * @param request servlet request bound to the current thread; never {@code null}.
     * @return non-blank, lower-case, ASCII-safe tenant identifier; never {@code null}.
     */
    String resolveTenant(HttpServletRequest request);
}

