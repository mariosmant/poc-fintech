package com.mariosmant.fintech.infrastructure.security.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * distributed token-bucket {@link RateLimiter}
 * backed by Bucket4j on Redis (Lettuce, CAS-based proxy manager).
 * policy-aware: a {@link BucketConfiguration} is
 * memoised per {@link RateLimitPolicy#id()} so each policy ships its own
 * bucket sizing.
 *
 * <h2>Failure modes</h2>
 * <p>On Redis errors ({@link RedisException}, network timeouts, etc.) the
 * adapter <b>fails open</b> by default — the request proceeds and a WARN log
 * is emitted at most once per minute. Set
 * {@code app.security.rate-limit.fail-open=false} to fail closed (return 429).
 * Adds a {@link CircuitBreakingRateLimiter} decorator that turns this
 * fail-open into a deterministic in-process Caffeine fallback.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public class Bucket4jRedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(Bucket4jRedisRateLimiter.class);

    private final boolean failOpen;
    private final String keyPrefix;
    private final RedisClient redisClient;
    private final int ttlSeconds;
    /** Lazily-initialised — building it eagerly would open a connection in the
     *  constructor and prevent fail-open if Redis is down at startup. */
    private volatile LettuceBasedProxyManager<byte[]> proxyManager;
    /** {@code policyId → BucketConfiguration} — built lazily, immutable per policy. */
    private final ConcurrentMap<String, Supplier<BucketConfiguration>> configCache = new ConcurrentHashMap<>();
    private final AtomicLong lastBackendErrorWarnNanos = new AtomicLong(0);

    public Bucket4jRedisRateLimiter(RedisClient redisClient, RateLimitProperties props) {
        this.failOpen = props.isFailOpen();
        this.keyPrefix = props.getRedis().getKeyPrefix();
        this.redisClient = redisClient;
        // TTL = 2 × default-policy window — bounds cold-key memory in Redis.
        // Active keys never expire because Bucket4j touches them on every consume.
        this.ttlSeconds = Math.max(60, props.getDefaultPolicy().getWindowSeconds() * 2);
        log.info("Bucket4jRedisRateLimiter — prefix='{}', fail-open={}, ttl={}s (policy-aware, lazy proxy)",
                keyPrefix, failOpen, ttlSeconds);
    }

    private LettuceBasedProxyManager<byte[]> proxyManager() {
        LettuceBasedProxyManager<byte[]> pm = this.proxyManager;
        if (pm == null) {
            synchronized (this) {
                pm = this.proxyManager;
                if (pm == null) {
                    pm = LettuceBasedProxyManager.builderFor(redisClient)
                            .withExpirationStrategy(
                                    ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                                            Duration.ofSeconds(ttlSeconds)))
                            .build();
                    this.proxyManager = pm;
                }
            }
        }
        return pm;
    }

    @Override
    public Decision tryConsume(String key, RateLimitPolicy policy) {
        // Per-policy Redis keys: rl:transfers:user:abc and rl:default:user:abc
        // are independent buckets.
        byte[] redisKey = (keyPrefix + policy.id() + ":" + key).getBytes(StandardCharsets.UTF_8);
        try {
            BucketProxy bucket = proxyManager().builder().build(redisKey, configFor(policy));
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (probe.isConsumed()) {
                long resetSec = Math.max(1L,
                        TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForReset()));
                return Decision.allow(policy.requestsPerMinute(), probe.getRemainingTokens(), resetSec);
            }
            long retryAfter = Math.max(1L,
                    TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
            return Decision.deny(policy.requestsPerMinute(), retryAfter);
        } catch (RedisException | IllegalStateException ex) {
            // Network blips / Redis down / CAS contention exhausted — RE-THROW so
            // the CircuitBreakingRateLimiter decorator can record the
            // failure and trip. Without a wrapping CB, the limiter is configured
            // either via fail-open=true (let through, only seen in dev) or
            // fail-open=false (return 429 — fail-closed). Both still flow through
            // here.
            warnOnceAMinute(ex);
            if (failOpen) {
                return Decision.allow(policy.requestsPerMinute(),
                        policy.requestsPerMinute(), policy.windowSeconds());
            }
            // For the CB decorator path we want the exception to surface; for the
            // bare-adapter fail-closed path we synthesise a deny.
            throw ex;
        }
    }

    private Supplier<BucketConfiguration> configFor(RateLimitPolicy policy) {
        return configCache.computeIfAbsent(policy.id(), id -> {
            BucketConfiguration cfg = BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(policy.burstCapacity())
                            .refillIntervally(policy.requestsPerMinute(),
                                    Duration.ofSeconds(policy.windowSeconds()))
                            .build())
                    .build();
            log.info("Bucket4jRedisRateLimiter — registered policy '{}' rpm={} window={}s burst={}",
                    id, policy.requestsPerMinute(), policy.windowSeconds(), policy.burstCapacity());
            return () -> cfg;
        });
    }

    private void warnOnceAMinute(Throwable t) {
        long now = System.nanoTime();
        long last = lastBackendErrorWarnNanos.get();
        if (now - last >= TimeUnit.MINUTES.toNanos(1)
                && lastBackendErrorWarnNanos.compareAndSet(last, now)) {
            log.warn("Rate-limiter Redis backend error (fail-open={}): {}",
                    failOpen, t.toString());
        }
    }
}


