package com.mariosmant.fintech.infrastructure.security.ratelimit;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * proves the {@link CircuitBreakingRateLimiter}:
 * <ol>
 *   <li>passes calls through to the primary while CLOSED;</li>
 *   <li>trips OPEN after enough primary failures;</li>
 *   <li>delegates to the in-process fallback while OPEN.</li>
 * </ol>
 */
class CircuitBreakingRateLimiterTest {

    private static final RateLimitPolicy POLICY = RateLimitPolicy.fixedWindow("default", 100, 60);

    @Test
    @DisplayName("CLOSED — primary handles the request")
    void closedDelegatesToPrimary() {
        CountingLimiter primary = new CountingLimiter(false);
        CountingLimiter fallback = new CountingLimiter(false);
        CircuitBreakingRateLimiter cb =
                new CircuitBreakingRateLimiter(primary, fallback, cfg());

        cb.tryConsume("k", POLICY);

        assertThat(primary.count.get()).isEqualTo(1);
        assertThat(fallback.count.get()).isZero();
        assertThat(cb.currentState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("OPEN — sustained primary failures trip the breaker; fallback takes over")
    void opensOnSustainedFailures() {
        CountingLimiter primary = new CountingLimiter(true);   // always throws
        CountingLimiter fallback = new CountingLimiter(false);
        CircuitBreakingRateLimiter cb =
                new CircuitBreakingRateLimiter(primary, fallback, cfg());

        // Drive minimumNumberOfCalls (5) failures — every call still returns
        // a valid Decision because the decorator catches and routes to fallback.
        for (int i = 0; i < 10; i++) {
            RateLimiter.Decision d = cb.tryConsume("k", POLICY);
            assertThat(d).isNotNull();
        }

        // Breaker should have observed enough failures to trip OPEN.
        assertThat(cb.currentState()).isEqualTo(CircuitBreaker.State.OPEN);
        // Once OPEN, primary is no longer called (tryAcquirePermission denies).
        int primaryAfterOpen = primary.count.get();
        cb.tryConsume("k", POLICY);
        assertThat(primary.count.get()).isEqualTo(primaryAfterOpen);
        assertThat(fallback.count.get()).isPositive();
    }

    private static RateLimitProperties.CircuitBreaker cfg() {
        RateLimitProperties.CircuitBreaker cfg = new RateLimitProperties.CircuitBreaker();
        cfg.setEnabled(true);
        cfg.setFailureRateThreshold(50f);
        cfg.setSlidingWindowSize(5);
        cfg.setMinimumNumberOfCalls(5);
        cfg.setWaitDurationInOpenStateSeconds(30);
        cfg.setPermittedNumberOfCallsInHalfOpenState(2);
        return cfg;
    }

    /** Counts invocations; if {@code throwOnCall} then every consume throws. */
    static final class CountingLimiter implements RateLimiter {
        final AtomicInteger count = new AtomicInteger();
        final boolean throwOnCall;

        CountingLimiter(boolean throwOnCall) {
            this.throwOnCall = throwOnCall;
        }

        @Override
        public Decision tryConsume(String key, RateLimitPolicy policy) {
            count.incrementAndGet();
            if (throwOnCall) {
                throw new IllegalStateException("simulated Redis outage");
            }
            return Decision.allow(policy.requestsPerMinute(), policy.requestsPerMinute() - 1, policy.windowSeconds());
        }
    }
}

