package com.mariosmant.fintech.infrastructure.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * bound configuration for the rate limiter port + adapters.
 * adds {@code default-policy}, per-route {@code policies},
 * and the optional Resilience4j circuit-breaker decorator.
 *
 * <p>Property prefix: {@code app.security.rate-limit}. Keys are
 * preserved verbatim and behave as the {@code default-policy} when set
 * directly on the prefix (back-compat shim — see {@link #getDefaultPolicy()}).</p>
 *
 * <h2>Per-route policies</h2>
 * <pre>
 * app.security.rate-limit:
 *   default-policy:
 *     requests-per-minute: 100
 *     window-seconds: 60
 *   policies:
 *     transfers:
 *       path-pattern: /api/v1/transfers/**
 *       requests-per-minute: 20
 *       window-seconds: 60
 *       burst-capacity: 5
 *     auth:
 *       path-pattern: /api/auth/**
 *       requests-per-minute: 30
 *       window-seconds: 60
 * </pre>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "app.security.rate-limit")
public class RateLimitProperties {

    /** {@code caffeine} (default) or {@code redis}. */
    public enum Backend { CAFFEINE, REDIS }

    // ── Back-compat single-policy fields (used as default-policy fallback) ──
    private int requestsPerMinute = 100;
    private int windowSeconds = 60;
    /** Defaults to {@link #requestsPerMinute} when left at {@code 0}. */
    private int burstCapacity = 0;
    private int maxTrackedKeys = 50_000;
    private Backend backend = Backend.CAFFEINE;
    private boolean failOpen = true;
    private final Redis redis = new Redis();

    /** Catch-all policy used when no per-route pattern matches. */
    private final PolicyDef defaultPolicy = new PolicyDef();
    /** Ordered map of per-route policies; YAML order is preserved. */
    private final Map<String, PolicyDef> policies = new LinkedHashMap<>();
    /** Resilience4j circuit-breaker wrapping the Redis adapter. */
    private final CircuitBreaker circuitBreaker = new CircuitBreaker();

    public int getRequestsPerMinute() { return requestsPerMinute; }
    public void setRequestsPerMinute(int v) { this.requestsPerMinute = v; }

    public int getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(int v) { this.windowSeconds = v; }

    public int getBurstCapacity() { return burstCapacity > 0 ? burstCapacity : requestsPerMinute; }
    public void setBurstCapacity(int v) { this.burstCapacity = v; }

    public int getMaxTrackedKeys() { return maxTrackedKeys; }
    public void setMaxTrackedKeys(int v) { this.maxTrackedKeys = v; }

    public Backend getBackend() { return backend; }
    public void setBackend(Backend v) { this.backend = v; }

    public boolean isFailOpen() { return failOpen; }
    public void setFailOpen(boolean v) { this.failOpen = v; }

    public Redis getRedis() { return redis; }

    /**
     * Default policy. Falls back to the back-compat single-policy fields
     * ({@code requests-per-minute}, {@code window-seconds}, {@code burst-capacity})
     * when the {@code default-policy.*}.
     */
    public PolicyDef getDefaultPolicy() {
        // Lazy back-compat shim: only honoured when default-policy.requests-per-minute
        // hasn't been explicitly set. The PolicyDef default is 0 so we can distinguish.
        if (defaultPolicy.requestsPerMinute <= 0) {
            defaultPolicy.requestsPerMinute = requestsPerMinute;
        }
        if (defaultPolicy.windowSeconds <= 0) {
            defaultPolicy.windowSeconds = windowSeconds;
        }
        if (defaultPolicy.burstCapacity <= 0) {
            defaultPolicy.burstCapacity = getBurstCapacity();
        }
        return defaultPolicy;
    }

    public Map<String, PolicyDef> getPolicies() { return policies; }

    public CircuitBreaker getCircuitBreaker() { return circuitBreaker; }

    public static class Redis {
        private String keyPrefix = "rl:";
        public String getKeyPrefix() { return keyPrefix; }
        public void setKeyPrefix(String v) { this.keyPrefix = v; }
    }

    /**
     * One bucket policy. Unset numeric fields are 0 so the
     * {@link PathPatternBucketPolicyResolver} / {@link #getDefaultPolicy()}
     * shim can detect "left at default" vs "explicitly set to N".
     */
    public static class PolicyDef {
        private String pathPattern;
        private int requestsPerMinute;
        private int windowSeconds;
        private int burstCapacity;

        public String getPathPattern() { return pathPattern; }
        public void setPathPattern(String v) { this.pathPattern = v; }

        public int getRequestsPerMinute() { return requestsPerMinute; }
        public void setRequestsPerMinute(int v) { this.requestsPerMinute = v; }

        public int getWindowSeconds() { return windowSeconds; }
        public void setWindowSeconds(int v) { this.windowSeconds = v; }

        public int getBurstCapacity() { return burstCapacity; }
        public void setBurstCapacity(int v) { this.burstCapacity = v; }
    }

    /**
     * Resilience4j circuit-breaker wrapping the Bucket4j-Redis adapter.
     * {@code enabled=false} (default). When
     * {@code enabled=true} the limiter falls back to an in-process Caffeine
     * sidecar for the duration of the {@code wait-duration-in-open-state}
     * after the failure-rate threshold trips.
     */
    public static class CircuitBreaker {
        private boolean enabled = false;
        private float failureRateThreshold = 50f;          // %
        private int slidingWindowSize = 20;                 // call count
        private int minimumNumberOfCalls = 5;
        private int waitDurationInOpenStateSeconds = 30;
        private int permittedNumberOfCallsInHalfOpenState = 3;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public float getFailureRateThreshold() { return failureRateThreshold; }
        public void setFailureRateThreshold(float v) { this.failureRateThreshold = v; }
        public int getSlidingWindowSize() { return slidingWindowSize; }
        public void setSlidingWindowSize(int v) { this.slidingWindowSize = v; }
        public int getMinimumNumberOfCalls() { return minimumNumberOfCalls; }
        public void setMinimumNumberOfCalls(int v) { this.minimumNumberOfCalls = v; }
        public int getWaitDurationInOpenStateSeconds() { return waitDurationInOpenStateSeconds; }
        public void setWaitDurationInOpenStateSeconds(int v) { this.waitDurationInOpenStateSeconds = v; }
        public int getPermittedNumberOfCallsInHalfOpenState() { return permittedNumberOfCallsInHalfOpenState; }
        public void setPermittedNumberOfCallsInHalfOpenState(int v) { this.permittedNumberOfCallsInHalfOpenState = v; }
    }
}
