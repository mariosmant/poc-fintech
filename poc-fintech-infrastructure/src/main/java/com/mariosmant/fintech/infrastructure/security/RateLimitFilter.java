package com.mariosmant.fintech.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-user rate limiting filter to protect against abuse and DoS.
 * Uses a simple sliding window counter per user (from JWT subject).
 *
 * <p>Returns 429 Too Many Requests with Retry-After header when limit exceeded.</p>
 *
 * <p>Production note: In a distributed environment, replace with Redis-based
 * rate limiting (e.g., Bucket4j + Redis) for consistent limiting across instances.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Value("${app.rate-limit.requests-per-minute:100}")
    private int requestsPerMinute;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Only rate-limit API endpoints
        if (!request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = resolveRateLimitKey(request);
        WindowCounter counter = counters.computeIfAbsent(key, k -> new WindowCounter());

        if (!counter.tryAcquire(requestsPerMinute)) {
            log.warn("Rate limit exceeded for key={}", key);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setContentType("application/json");
            response.getWriter().write("""
                    {"type":"urn:fintech:error:rate-limit","title":"Too Many Requests","status":429,"detail":"Rate limit exceeded. Try again later."}""");
            return;
        }

        response.setHeader("X-RateLimit-Remaining",
                String.valueOf(Math.max(0, requestsPerMinute - counter.getCount())));
        filterChain.doFilter(request, response);
    }

    private String resolveRateLimitKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return "user:" + jwtAuth.getToken().getSubject();
        }
        // Fall back to IP for unauthenticated requests
        return "ip:" + request.getRemoteAddr();
    }

    /**
     * Simple sliding-window counter with 1-minute resolution.
     */
    private static class WindowCounter {
        private volatile long windowStart = System.currentTimeMillis();
        private final AtomicInteger count = new AtomicInteger(0);

        boolean tryAcquire(int limit) {
            long now = System.currentTimeMillis();
            if (now - windowStart > 60_000) {
                synchronized (this) {
                    if (now - windowStart > 60_000) {
                        windowStart = now;
                        count.set(0);
                    }
                }
            }
            return count.incrementAndGet() <= limit;
        }

        int getCount() {
            return count.get();
        }
    }
}

