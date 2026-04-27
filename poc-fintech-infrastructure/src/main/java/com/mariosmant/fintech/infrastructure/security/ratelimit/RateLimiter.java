package com.mariosmant.fintech.infrastructure.security.ratelimit;

/**
 * rate-limiter port.
 * accepts a per-request {@link RateLimitPolicy} so
 * each route can size its own bucket.
 *
 * <p>The servlet filter ({@code RateLimitFilter}) depends only on this
 * interface; concrete adapters live in this package and are selected at
 * boot time by the {@code app.security.rate-limit.backend} property:</p>
 * <ul>
 *   <li>{@code caffeine} (default) — {@link CaffeineRateLimiter}, in-process
 *       bounded sliding window.</li>
 *   <li>{@code redis} — {@link Bucket4jRedisRateLimiter}, distributed token
 *       bucket on Redis via Lettuce.</li>
 * </ul>
 *
 * <p>When the Redis adapter is selected, it is wrapped by
 * {@link CircuitBreakingRateLimiter} so a Redis outage gracefully degrades
 * to an in-process Caffeine fallback.</p>
 *
 * <p>An ArchUnit fitness function in {@code HexagonalArchitectureTest}
 * forbids the filter from depending on Bucket4j, Lettuce, or Caffeine
 * directly so the boundary cannot silently regress.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public interface RateLimiter {

    /**
     * Attempt to consume one request token for {@code key} under {@code policy}.
     *
     * @param key    opaque identity ({@code user:<sub>} or {@code ip:<addr>});
     *               adapters MUST namespace this with the policy id internally
     *               so different policies do not share buckets.
     * @param policy bucket sizing for this request (route- and/or principal-
     *               specific). MUST NOT be {@code null}.
     * @return decision carrying allow/deny + headers metadata.
     */
    Decision tryConsume(String key, RateLimitPolicy policy);

    /**
     * Rate-limiter decision.
     *
     * @param allowed           whether the request may proceed.
     * @param limit             effective limit ({@code policy.requestsPerMinute()})
     *                          for this decision; surfaced as {@code RateLimit-Limit}.
     * @param remaining         remaining tokens in the current window
     *                          (clamped to ≥ 0); surfaced as {@code RateLimit-Remaining}.
     * @param retryAfterSeconds delta-seconds until the window refills enough
     *                          to allow another request — surfaced both in the
     *                          {@code Retry-After} (on 429) and
     *                          {@code RateLimit-Reset} headers
     *                          (draft-ietf-httpapi-ratelimit-headers).
     */
    record Decision(boolean allowed, long limit, long remaining, long retryAfterSeconds) {
        public static Decision allow(long limit, long remaining, long resetSeconds) {
            return new Decision(true, limit, Math.max(0, remaining), Math.max(0, resetSeconds));
        }
        public static Decision deny(long limit, long retryAfterSeconds) {
            return new Decision(false, limit, 0L, Math.max(1, retryAfterSeconds));
        }
    }
}
