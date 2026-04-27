package com.mariosmant.fintech.infrastructure.security.ratelimit;

import com.mariosmant.fintech.infrastructure.security.RateLimitFilter;
import com.mariosmant.fintech.infrastructure.security.tenant.JwtClaimTenantResolver;
import com.mariosmant.fintech.infrastructure.security.tenant.TenantResolver;
import io.lettuce.core.RedisClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires the {@link RateLimiter} adapter chosen by
 * {@code app.security.rate-limit.backend}.
 * Also wires the {@link BucketPolicyResolver} and the
 * optional {@link CircuitBreakingRateLimiter} decorator.
 *
 * <p>Backend selection is a single property. {@code caffeine} (the default)
 * preserves the in-process behaviour with zero new transitive
 * dependencies on the request path. {@code redis} swaps in the Bucket4j
 * provided by {@code spring-boot-starter-data-redis}. When
 * {@code app.security.rate-limit.circuit-breaker.enabled=true} the Redis
 * adapter is wrapped by a Resilience4j circuit breaker that falls back to
 * an in-process Caffeine sidecar on Redis errors.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);

    /**
     * picks the {@link RateLimitPolicy} for each request based on
     * an Ant-style path pattern list. Unmatched requests fall back to the
     * configured default policy.
     */
    @Bean
    public BucketPolicyResolver bucketPolicyResolver(RateLimitProperties props) {
        return new PathPatternBucketPolicyResolver(props);
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.rate-limit.backend",
            havingValue = "caffeine", matchIfMissing = true)
    public RateLimiter caffeineRateLimiter(RateLimitProperties props) {
        if (props.getBackend() == RateLimitProperties.Backend.REDIS) {
            log.warn("Rate-limit backend property says REDIS but Redis adapter could not be wired; "
                    + "falling back to Caffeine. Verify spring-boot-starter-data-redis + RedisConnectionFactory.");
        }
        return new CaffeineRateLimiter(
                props.getMaxTrackedKeys(),
                props.getDefaultPolicy().getWindowSeconds());
    }

    /**
     * Bucket4j on Redis. When the circuit breaker is enabled it is
     * wrapped by {@link CircuitBreakingRateLimiter}; otherwise the bare
     * adapter is exposed as the {@link RateLimiter} bean.
     */
    @Bean
    @ConditionalOnProperty(name = "app.security.rate-limit.backend", havingValue = "redis")
    public RateLimiter bucket4jRedisRateLimiter(RedisConnectionFactory connectionFactory,
                                                RateLimitProperties props) {
        if (!(connectionFactory instanceof LettuceConnectionFactory lettuce)) {
            throw new IllegalStateException(
                    "app.security.rate-limit.backend=redis requires a LettuceConnectionFactory; "
                            + "got " + connectionFactory.getClass().getName());
        }
        Object native_ = lettuce.getNativeClient();
        if (!(native_ instanceof RedisClient redisClient)) {
            throw new IllegalStateException(
                    "LettuceConnectionFactory#getNativeClient() returned " + native_
                            + "; expected a standalone RedisClient (clustered Redis is not yet supported).");
        }
        if (props.getMaxTrackedKeys() != 50_000) {
            log.info("app.security.rate-limit.max-tracked-keys is ignored when backend=redis "
                    + "(Bucket4j stores state in Redis with TTL = 2 × default-policy window).");
        }
        Bucket4jRedisRateLimiter primary = new Bucket4jRedisRateLimiter(redisClient, props);
        if (!props.getCircuitBreaker().isEnabled()) {
            return primary;
        }
        // graceful degradation. Caffeine sidecar handles fallback.
        CaffeineRateLimiter fallback = new CaffeineRateLimiter(
                props.getMaxTrackedKeys(),
                props.getDefaultPolicy().getWindowSeconds());
        return new CircuitBreakingRateLimiter(primary, fallback, props.getCircuitBreaker());
    }

    /**
     * Wires the {@link RateLimitFilter} as a {@code @Bean} (not a
     * {@code @Component}) so it is created together with — and only when —
     * the {@link RateLimiter} adapter is wired. This keeps {@code @WebMvcTest}
     * slices that don't pull in this configuration from failing on a missing
     * {@code RateLimiter} dependency.
     */
    @Bean
    public RateLimitFilter rateLimitFilter(RateLimiter rateLimiter,
                                           BucketPolicyResolver policyResolver,
                                           TenantResolver tenantResolver,
                                           ObjectMapper objectMapper) {
        return new RateLimitFilter(rateLimiter, policyResolver, tenantResolver, objectMapper);
    }

    /**
     * default {@link TenantResolver}. Reads
     * {@code tenant_id} / {@code tid} / {@code azp} from the JWT and falls
     * back to {@link TenantResolver#SHARED_TENANT} for unauthenticated
     * requests or tokens that don't carry a tenant claim. Operators with a
     * non-JWT tenant model (header-derived, path-derived, …) supply their
     * own bean and this default backs off.
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(TenantResolver.class)
    public TenantResolver tenantResolver() {
        return new JwtClaimTenantResolver();
    }
}

