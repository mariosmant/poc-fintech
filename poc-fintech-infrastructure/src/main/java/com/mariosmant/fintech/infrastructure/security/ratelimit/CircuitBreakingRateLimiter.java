package com.mariosmant.fintech.infrastructure.security.ratelimit;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Resilience4j circuit-breaker decorator.
 *
 * <h2>Why a circuit breaker?</h2>
 * <p>The Bucket4j-Redis adapter with a binary
 * {@code fail-open} switch:</p>
 * <ul>
 *   <li>{@code fail-open=true}  — Redis blip ⇒ silently let traffic through
 *       (effective limit reverts to {@code N × per-pod}).</li>
 *   <li>{@code fail-open=false} — Redis blip ⇒ 429 every request until
 *       Redis comes back (DoS the API on top of the storage outage).</li>
 * </ul>
 * <p>Neither is great. The circuit breaker gives a third option:</p>
 * <ol>
 *   <li><b>CLOSED</b> (steady state): all calls hit Bucket4j-Redis.</li>
 *   <li><b>OPEN</b> (after the failure-rate threshold trips): all calls
 *       short-circuit to an in-process Caffeine fallback for the configured
 *       {@code wait-duration-in-open-state}. Limiting still happens, just
 *       per-pod instead of cluster-wide. Latency drops to nanoseconds while
 *       Redis recovers.</li>
 *   <li><b>HALF_OPEN</b>: a small probe set is allowed through; success
 *       closes the breaker, failure re-opens it.</li>
 * </ol>
 *
 * <h2>Standards mapping</h2>
 * <ul>
 *   <li>NIST SP 800-53 Rev 5 — SC-5(2) Manage Capacity, Bandwidth.</li>
 *   <li>OWASP ASVS 4.0.3 — V11.1.4 (resource-consumption protection).</li>
 *   <li>SRE Book ch. 22 — Cascading Failures.</li>
 * </ul>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public class CircuitBreakingRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakingRateLimiter.class);

    private final RateLimiter primary;
    private final RateLimiter fallback;
    private final CircuitBreaker breaker;

    public CircuitBreakingRateLimiter(RateLimiter primary,
                                      RateLimiter fallback,
                                      RateLimitProperties.CircuitBreaker cfg) {
        this.primary = primary;
        this.fallback = fallback;
        this.breaker = CircuitBreaker.of("rate-limiter-redis", CircuitBreakerConfig.custom()
                .failureRateThreshold(cfg.getFailureRateThreshold())
                .slidingWindowSize(cfg.getSlidingWindowSize())
                .minimumNumberOfCalls(cfg.getMinimumNumberOfCalls())
                .waitDurationInOpenState(Duration.ofSeconds(cfg.getWaitDurationInOpenStateSeconds()))
                .permittedNumberOfCallsInHalfOpenState(cfg.getPermittedNumberOfCallsInHalfOpenState())
                // Treat ANY thrown exception from the primary as a failure —
                // we don't care if it's a network blip, a Redis CLUSTERDOWN,
                // or a CAS exhaustion. The fallback handles them identically.
                .recordExceptions(Exception.class)
                .build());

        breaker.getEventPublisher()
                .onStateTransition(e -> log.warn("CircuitBreaker[rate-limiter-redis] {} -> {}",
                        e.getStateTransition().getFromState(),
                        e.getStateTransition().getToState()));

        log.info("CircuitBreakingRateLimiter wired — primary={}, fallback={}, threshold={}%, window={} calls",
                primary.getClass().getSimpleName(),
                fallback.getClass().getSimpleName(),
                cfg.getFailureRateThreshold(),
                cfg.getSlidingWindowSize());
    }

    @Override
    public Decision tryConsume(String key, RateLimitPolicy policy) {
        if (!breaker.tryAcquirePermission()) {
            // OPEN ⇒ short-circuit straight to the in-process fallback. The
            // Caffeine adapter is policy-aware so the bucket sizing is
            // identical; only the cluster-wide aggregation is lost while
            // Redis recovers.
            return fallback.tryConsume(key, policy);
        }
        long start = System.nanoTime();
        try {
            Decision decision = primary.tryConsume(key, policy);
            breaker.onResult(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS, decision);
            return decision;
        } catch (CallNotPermittedException cnpe) {
            // Race between tryAcquirePermission() and onResult(); fall through.
            return fallback.tryConsume(key, policy);
        } catch (Exception ex) {
            breaker.onError(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS, ex);
            // Use the in-process fallback for THIS request too — we must always
            // return a Decision, never propagate the storage error to the caller.
            return fallback.tryConsume(key, policy);
        }
    }

    /** Visible-for-tests. */
    public CircuitBreaker.State currentState() {
        return breaker.getState();
    }
}

