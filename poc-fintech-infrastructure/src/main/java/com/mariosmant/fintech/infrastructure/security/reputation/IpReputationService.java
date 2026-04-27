package com.mariosmant.fintech.infrastructure.security.reputation;

/**
 *port for IP-reputation lookups.
 *
 * <p>Implementations evaluate whether the supplied client IP appears on a
 * curated block-list (Spamhaus DROP, Cloudflare AS lists, internal abuse
 * feed). Lookups MUST be cheap (sub-microsecond) — the call sits on every
 * inbound request hot-path; production adapters keep the parsed CIDR set
 * in memory and refresh asynchronously on a {@code @Scheduled} cycle.</p>
 *
 * <p>The port is intentionally agnostic to the feed format:
 * {@link CaffeineIpReputationService} ships with the POC; future adapters
 * (Cloudflare AS-list, MaxMind GeoIP-anonymous) plug in behind the same
 * interface.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public interface IpReputationService {

    /**
     * @param remoteAddr dotted-quad IPv4 or RFC 4291 IPv6 string. Implementations
     *                   tolerate {@code null} / blank / malformed input by
     *                   returning {@code false} (fail-open at the per-call layer
     *                   — the upstream gateway is the authoritative deny path).
     * @return {@code true} when the address is on the active block-list.
     */
    boolean isBlocked(String remoteAddr);

    /**
     * Approximate count of currently-blocked entries (CIDR ranges or single IPs).
     * Useful for {@code /actuator/info} and ops dashboards. Implementations
     * MUST return a non-negative value; the empty service returns {@code 0}.
     */
    int size();
}

