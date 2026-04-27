package com.mariosmant.fintech.infrastructure.security.ratelimit;

import java.time.Duration;

/**
 * immutable bucket policy.
 *
 * <p>Carries everything a {@link RateLimiter} adapter needs to size a bucket
 * for a given request: refill rate ({@code requestsPerMinute}), refill window,
 * and burst capacity. Resolved per-request by a {@link BucketPolicyResolver}.</p>
 *
 * <h2>Identity</h2>
 * <p>The {@link #id} is appended to the limiter key so each policy gets its
 * own bucket per principal — {@code transfers:user:&lt;sub&gt;} cannot eat
 * tokens from {@code default:user:&lt;sub&gt;}. Without that segregation a
 * stricter policy would shrink the headroom of a more permissive one.</p>
 *
 * <h2>Validation</h2>
 * <p>{@code requestsPerMinute &gt; 0}, {@code windowSeconds &gt; 0},
 * {@code burstCapacity &gt;= requestsPerMinute} (a smaller burst would be
 * meaningless: the bucket couldn't hold a single window's worth of refill).</p>
 *
 * @param id                policy id ({@code "default"}, {@code "transfers"}, …);
 *                          appended to the limiter key for per-policy isolation.
 * @param requestsPerMinute tokens added per {@link #windowSeconds()}.
 * @param windowSeconds     refill window length.
 * @param burstCapacity     bucket capacity. Must be &ge; {@code requestsPerMinute}.
 *
 * @author mariosmant
 * @since 1.0.0
 */
public record RateLimitPolicy(String id, int requestsPerMinute, int windowSeconds, int burstCapacity) {

    public RateLimitPolicy {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("policy id must be non-blank");
        }
        if (requestsPerMinute <= 0) {
            throw new IllegalArgumentException("requestsPerMinute must be positive: " + requestsPerMinute);
        }
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("windowSeconds must be positive: " + windowSeconds);
        }
        if (burstCapacity < requestsPerMinute) {
            // Coerce instead of throwing — silently accepting tiny bursts would
            // confuse operators ("why is my limit 5 when I asked for 100?"),
            // but for the default property layout (burst=0 ⇒ = rpm) this branch
            // is never hit because the resolver normalises 0 → rpm first.
            throw new IllegalArgumentException(
                    "burstCapacity (" + burstCapacity + ") must be >= requestsPerMinute ("
                            + requestsPerMinute + ") for policy '" + id + "'");
        }
    }

    /** Convenience: {@code windowSeconds} as a {@link Duration}. */
    public Duration window() {
        return Duration.ofSeconds(windowSeconds);
    }

    /**
     * Build a policy that reproduces the fixed-window behaviour
     * (burst == refill rate ⇒ no bursting beyond the steady-state limit).
     */
    public static RateLimitPolicy fixedWindow(String id, int requestsPerMinute, int windowSeconds) {
        return new RateLimitPolicy(id, requestsPerMinute, windowSeconds, requestsPerMinute);
    }
}

