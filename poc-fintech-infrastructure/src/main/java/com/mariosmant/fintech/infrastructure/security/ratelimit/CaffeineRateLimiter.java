package com.mariosmant.fintech.infrastructure.security.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * behaviour as a {@link RateLimiter} adapter.
 * policy-aware: each {@code (policyId, key)} pair gets
 * its own sliding window so {@code transfers} requests cannot drain the
 * {@code default} bucket and vice versa.
 *
 * <p>In-process, sliding-window counter backed by a Caffeine cache with a
 * hard size cap and {@code expireAfterAccess(2 × maxWindow)} TTL. The cache
 * cap defends against a long-tailed key set (one entry per IP) exhausting
 * heap.</p>
 *
 * <p>Per-instance — does not share state across pods. For horizontally-scaled
 * deployments select the {@link Bucket4jRedisRateLimiter} via
 * {@code app.security.rate-limit.backend=redis}.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public class CaffeineRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(CaffeineRateLimiter.class);

    private final Cache<String, WindowCounter> counters;
    /** Largest expected window in seconds — used to size the cache TTL safely. */
    private final int maxObservedWindowSeconds;

    public CaffeineRateLimiter(int maxTrackedKeys, int windowSecondsHint) {
        this.maxObservedWindowSeconds = Math.max(60, windowSecondsHint);
        // expireAfterAccess(2 × max window) — keys reactivated by traffic stay
        // alive; cold keys are reaped automatically.
        this.counters = Caffeine.newBuilder()
                .maximumSize(maxTrackedKeys)
                .expireAfterAccess(Duration.ofSeconds(maxObservedWindowSeconds * 2L))
                .build();
        log.info("CaffeineRateLimiter — max-tracked-keys={}, ttl=2×{}s (policy-aware)",
                maxTrackedKeys, maxObservedWindowSeconds);
    }

    @Override
    public Decision tryConsume(String key, RateLimitPolicy policy) {
        // Per-policy isolation: a strict "transfers" policy doesn't share
        // tokens with a permissive "default" policy for the same principal.
        String composed = policy.id() + ":" + key;
        long windowMillis = policy.windowSeconds() * 1000L;
        WindowCounter counter = counters.get(composed, k -> new WindowCounter(windowMillis));
        long now = System.currentTimeMillis();
        boolean allowed = counter.tryAcquire(policy.requestsPerMinute(), now, windowMillis);
        long resetSec = Math.max(1L, (counter.windowStart + windowMillis - now) / 1000L);
        if (!allowed) {
            return Decision.deny(policy.requestsPerMinute(), resetSec);
        }
        return Decision.allow(
                policy.requestsPerMinute(),
                Math.max(0, policy.requestsPerMinute() - counter.getCount()),
                resetSec);
    }

    /** Visible-for-tests: how many keys are currently tracked in the cache. */
    public long trackedKeyCount() {
        counters.cleanUp();
        return counters.estimatedSize();
    }

    /**
     * Sliding-window counter — atomic increment within a window, atomic
     * window-rollover on first access past the boundary.
     */
    static final class WindowCounter {
        private final long initialWindowMillis;
        volatile long windowStart;
        private final AtomicInteger count;

        WindowCounter(long initialWindowMillis) {
            this.initialWindowMillis = initialWindowMillis;
            this.windowStart = System.currentTimeMillis();
            this.count = new AtomicInteger(0);
        }

        boolean tryAcquire(int limit, long now, long windowMillis) {
            if (now - windowStart > windowMillis) {
                synchronized (this) {
                    if (now - windowStart > windowMillis) {
                        windowStart = now;
                        count.set(0);
                    }
                }
            }
            return count.incrementAndGet() <= limit;
        }

        int getCount() { return count.get(); }
    }
}
