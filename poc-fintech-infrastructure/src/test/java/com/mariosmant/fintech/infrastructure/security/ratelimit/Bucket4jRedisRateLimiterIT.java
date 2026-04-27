package com.mariosmant.fintech.infrastructure.security.ratelimit;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers integration test for
 * {@link Bucket4jRedisRateLimiter} against a real Redis 7 container.
 *
 * <p>Asserts the production-shape contract: token consume, deny-on-burst,
 * and fail-open after the container is stopped.</p>
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Bucket4jRedisRateLimiterIT {

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    private RedisClient client;

    @BeforeAll
    void start() {
        REDIS.start();
        client = RedisClient.create(RedisURI.create(REDIS.getHost(), REDIS.getMappedPort(6379)));
    }

    @AfterAll
    void stop() {
        if (client != null) client.shutdown();
        REDIS.stop();
    }

    @Test
    @DisplayName("Allows up to burst, denies the next, returns Retry-After hint")
    void enforcesLimit() {
        RateLimitProperties p = props(/*rpm*/ 3, /*window*/ 60, /*burst*/ 3, /*failOpen*/ true);
        Bucket4jRedisRateLimiter limiter = new Bucket4jRedisRateLimiter(client, p);
        RateLimitPolicy policy = new RateLimitPolicy("default", 3, 60, 3);
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryConsume("user:alice", policy).allowed()).isTrue();
        }
        RateLimiter.Decision denied = limiter.tryConsume("user:alice", policy);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isPositive();
    }

    @Test
    @DisplayName("Different keys are tracked independently")
    void perKeyIsolation() {
        RateLimitProperties p = props(2, 60, 2, true);
        Bucket4jRedisRateLimiter limiter = new Bucket4jRedisRateLimiter(client, p);
        RateLimitPolicy policy = new RateLimitPolicy("default", 2, 60, 2);
        assertThat(limiter.tryConsume("user:bob", policy).allowed()).isTrue();
        assertThat(limiter.tryConsume("user:bob", policy).allowed()).isTrue();
        assertThat(limiter.tryConsume("user:bob", policy).allowed()).isFalse();
        // Different key — fresh bucket.
        assertThat(limiter.tryConsume("user:carol", policy).allowed()).isTrue();
    }

    @Test
    @DisplayName("Fail-open: when Redis is unreachable, the request is allowed through")
    void failOpenOnRedisOutage() {
        // Point at an unreachable Redis instance.
        RedisClient broken = RedisClient.create(RedisURI.create("127.0.0.1", 1));
        try {
            Bucket4jRedisRateLimiter limiter = new Bucket4jRedisRateLimiter(broken, props(3, 60, 3, true));
            RateLimitPolicy policy = new RateLimitPolicy("default", 3, 60, 3);
            RateLimiter.Decision d = limiter.tryConsume("user:dave", policy);
            assertThat(d.allowed()).isTrue();
        } finally {
            broken.shutdown();
        }
    }

    private static RateLimitProperties props(int rpm, int window, int burst, boolean failOpen) {
        RateLimitProperties p = new RateLimitProperties();
        p.setRequestsPerMinute(rpm);
        p.setWindowSeconds(window);
        p.setBurstCapacity(burst);
        p.setFailOpen(failOpen);
        p.setBackend(RateLimitProperties.Backend.REDIS);
        return p;
    }
}
