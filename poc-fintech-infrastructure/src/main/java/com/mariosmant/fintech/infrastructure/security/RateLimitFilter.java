package com.mariosmant.fintech.infrastructure.security;

import com.mariosmant.fintech.infrastructure.security.ratelimit.BucketPolicyResolver;
import com.mariosmant.fintech.infrastructure.security.ratelimit.RateLimitPolicy;
import com.mariosmant.fintech.infrastructure.security.ratelimit.RateLimiter;
import com.mariosmant.fintech.infrastructure.security.tenant.TenantResolver;
import com.mariosmant.fintech.infrastructure.web.exception.ProblemDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Per-principal rate-limiting filter.
 *
 * <h2>Architecture</h2>
 * <p>The filter depends only on the {@link RateLimiter} <b>port</b> and the
 * {@link BucketPolicyResolver} <b>port</b>; concrete adapters
 * ({@code CaffeineRateLimiter}, {@code Bucket4jRedisRateLimiter},
 * {@code CircuitBreakingRateLimiter}, {@code PathPatternBucketPolicyResolver})
 * are wired in {@code RateLimitConfig}. An ArchUnit fitness function in
 * {@code HexagonalArchitectureTest} forbids this class from depending on
 * Bucket4j, Lettuce, or Caffeine packages so the boundary cannot silently
 * regress.</p>
 *
 * <h2>Standards mapping</h2>
 * <ul>
 *   <li>RFC 6585 §4 — HTTP 429 Too Many Requests.</li>
 *   <li>RFC 7807 — Problem Details for HTTP APIs.</li>
 *   <li>{@code draft-ietf-httpapi-ratelimit-headers} — RateLimit-* fields.</li>
 *   <li>OWASP API Security Top 10 — API4:2023 Unrestricted Resource Consumption.</li>
 *   <li>NIST SP 800-53 SC-5 — Denial-of-Service Protection.</li>
 *   <li>OWASP ASVS V11.1 — Business-logic security (rate / volume).</li>
 * </ul>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimiter limiter;
    private final BucketPolicyResolver policyResolver;
    private final TenantResolver tenantResolver;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimiter limiter,
                           BucketPolicyResolver policyResolver,
                           TenantResolver tenantResolver,
                           ObjectMapper objectMapper) {
        this.limiter = limiter;
        this.policyResolver = policyResolver;
        this.tenantResolver = tenantResolver;
        this.objectMapper = objectMapper;
        log.info("RateLimitFilter — wired with limiter={} resolver={} tenantResolver={}",
                limiter.getClass().getSimpleName(),
                policyResolver.getClass().getSimpleName(),
                tenantResolver.getClass().getSimpleName());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Only rate-limit API endpoints — actuator, BFF metadata, OAuth2
        // redirects must remain reachable even under aggressive limits so
        // health probes and login flows aren't starved.
        if (!request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitPolicy policy = policyResolver.resolve(request);
        String key = resolveRateLimitKey(request);
        RateLimiter.Decision decision = limiter.tryConsume(key, policy);

        // IETF draft-ietf-httpapi-ratelimit-headers — emit on every response,
        // success or 429, so SDKs can implement client-side back-off.
        response.setHeader("RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader("RateLimit-Reset", String.valueOf(decision.retryAfterSeconds()));

        if (!decision.allowed()) {
            log.warn("Rate limit exceeded policy={} key={} retryAfter={}s",
                    policy.id(), key, decision.retryAfterSeconds());
            writeRateLimitedResponse(response, decision.retryAfterSeconds());
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Build the 429 body via {@link ProblemDetails} so the shape matches every
     * other error in the application — opaque {@code detail}, canonical
     * {@code type} URI {@code urn:fintech:error:rate-limit}, MDC-driven
     * {@code requestId}/{@code traceId}, ISO-8601 {@code timestamp}.
     */
    private void writeRateLimitedResponse(HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        ProblemDetail body = ProblemDetails.of(
                HttpStatus.TOO_MANY_REQUESTS,
                ProblemDetails.ErrorType.RATE_LIMITED,
                "Rate limit exceeded. Try again in " + retryAfterSeconds + " seconds.");
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private String resolveRateLimitKey(HttpServletRequest request) {
        // every key is namespaced by tenant. Single-
        // tenant deployments transparently get the "shared" prefix; multi-
        // tenant deployments can isolate buckets per tenant without a code
        // change to the limiter or its adapters.
        String tenant = tenantResolver.resolveTenant(request);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return "tenant:" + tenant + ":user:" + jwtAuth.getToken().getSubject();
        }
        // Fall back to IP for unauthenticated requests.
        return "tenant:" + tenant + ":ip:" + request.getRemoteAddr();
    }
}

