package com.mariosmant.fintech.infrastructure.security.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adapter-level proof for {@link CaffeineRateLimiter} policy-aware refactor.
 *
 * <p>The cache cap is preserved, and policies are isolated: a request
 * against the {@code transfers} policy must NOT consume a token from the
 * {@code default} policy for the same principal.</p>
 */
class CaffeineRateLimiterTest {

    private static final RateLimitPolicy DEFAULT = RateLimitPolicy.fixedWindow("default", 3, 60);
    private static final RateLimitPolicy STRICT = RateLimitPolicy.fixedWindow("transfers", 1, 60);

    @Test
    @DisplayName("Allows up to N consumes per (policy, key); deny on N+1")
    void enforcesLimit() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(100, 60);
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryConsume("ip:1.1.1.1", DEFAULT).allowed()).isTrue();
        }
        assertThat(limiter.tryConsume("ip:1.1.1.1", DEFAULT).allowed()).isFalse();
    }

    @Test
    @DisplayName("Cache size cap caps memory: oldest cold key is evicted past maxKeys")
    void boundedSize() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(/*maxKeys*/ 2, 60);
        for (int i = 0; i < 5; i++) {
            limiter.tryConsume("ip:10.0.0." + i, DEFAULT);
        }
        assertThat(limiter.trackedKeyCount()).isLessThanOrEqualTo(2L);
    }

    @Test
    @DisplayName("Decision exposes a reset hint and the policy's limit")
    void resetWithinWindow() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(100, 60);
        RateLimitPolicy policy = RateLimitPolicy.fixedWindow("default", 5, 60);
        RateLimiter.Decision d = limiter.tryConsume("k", policy);
        assertThat(d.allowed()).isTrue();
        assertThat(d.limit()).isEqualTo(5);
        assertThat(d.retryAfterSeconds()).isBetween(1L, 60L);
        assertThat(d.remaining()).isEqualTo(4);
    }

    @Test
    @DisplayName("policies are isolated: 'transfers' draining doesn't shrink 'default'")
    void policiesAreIsolated() {
        CaffeineRateLimiter limiter = new CaffeineRateLimiter(100, 60);
        // Drain the strict transfers bucket.
        assertThat(limiter.tryConsume("user:alice", STRICT).allowed()).isTrue();
        assertThat(limiter.tryConsume("user:alice", STRICT).allowed()).isFalse();
        // Default bucket is untouched.
        assertThat(limiter.tryConsume("user:alice", DEFAULT).allowed()).isTrue();
        assertThat(limiter.tryConsume("user:alice", DEFAULT).allowed()).isTrue();
        assertThat(limiter.tryConsume("user:alice", DEFAULT).allowed()).isTrue();
        assertThat(limiter.tryConsume("user:alice", DEFAULT).allowed()).isFalse();
    }
}
