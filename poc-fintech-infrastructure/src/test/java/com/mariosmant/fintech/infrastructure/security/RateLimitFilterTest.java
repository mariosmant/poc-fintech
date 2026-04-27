package com.mariosmant.fintech.infrastructure.security;

import com.mariosmant.fintech.infrastructure.security.ratelimit.BucketPolicyResolver;
import com.mariosmant.fintech.infrastructure.security.ratelimit.RateLimitPolicy;
import com.mariosmant.fintech.infrastructure.security.ratelimit.RateLimiter;
import com.mariosmant.fintech.infrastructure.security.tenant.TenantResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * unit tests — proves
 * {@link RateLimitFilter} delegates to the {@link RateLimiter} port + the
 * {@link BucketPolicyResolver} port, emits the IETF
 * {@code RateLimit-Limit/Remaining/Reset} triplet, no longer emits the
 * legacy {@code X-RateLimit-Remaining}, and renders 429 bodies through
 * {@link com.mariosmant.fintech.infrastructure.web.exception.ProblemDetails}
 * (RFC 7807 — {@code application/problem+json}).
 */
class RateLimitFilterTest {

    private static final RateLimitPolicy DEFAULT_POLICY =
            RateLimitPolicy.fixedWindow("default", 3, 60);
    private static final RateLimitPolicy STRICT_POLICY =
            RateLimitPolicy.fixedWindow("transfers", 1, 60);

    private FakeRateLimiter limiter;
    private RoutingPolicyResolver resolver;
    private RateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        limiter = new FakeRateLimiter();
        resolver = new RoutingPolicyResolver();
        // tests use the SHARED tenant. JWT-claim
        // resolution is exercised by JwtClaimTenantResolverTest in isolation.
        TenantResolver tenantResolver = req -> TenantResolver.SHARED_TENANT;
        filter = new RateLimitFilter(limiter, resolver, tenantResolver, new ObjectMapper());
        chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("First N requests pass; (N+1)th gets 429 + Retry-After + ProblemDetails; no legacy header")
    void enforcesLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse res = invoke("/api/v1/accounts", "1.2.3.4");
            assertThat(res.getStatus()).isEqualTo(200);
            assertThat(res.getHeader("RateLimit-Limit")).isEqualTo("3");
            assertThat(res.getHeader("RateLimit-Remaining")).isNotNull();
            assertThat(res.getHeader("RateLimit-Reset")).isNotNull();
            assertThat(res.getHeader("X-RateLimit-Remaining")).isNull();
        }
        MockHttpServletResponse over = invoke("/api/v1/accounts", "1.2.3.4");
        assertThat(over.getStatus()).isEqualTo(429);
        assertThat(over.getHeader("Retry-After")).isEqualTo("60");
        assertThat(over.getHeader("RateLimit-Limit")).isEqualTo("3");
        assertThat(over.getHeader("RateLimit-Remaining")).isEqualTo("0");
        assertThat(over.getHeader("X-RateLimit-Remaining")).isNull();
        assertThat(over.getContentType()).startsWith("application/problem+json");
        String body = over.getContentAsString();
        assertThat(body).contains("\"type\":\"urn:fintech:error:rate-limit\"");
        assertThat(body).contains("\"status\":429");
        assertThat(body).contains("\"title\":\"Too Many Requests\"");
        verify(chain, times(3)).doFilter(org.mockito.Mockito.any(), org.mockito.Mockito.any());
    }

    @Test
    @DisplayName("strict /api/v1/transfers policy denies after 1 request, /accounts still allowed")
    void perRoutePolicyApplied() throws Exception {
        // Strict policy on /transfers — limit=1.
        assertThat(invoke("/api/v1/transfers", "9.9.9.9").getStatus()).isEqualTo(200);
        MockHttpServletResponse second = invoke("/api/v1/transfers", "9.9.9.9");
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(second.getHeader("RateLimit-Limit")).isEqualTo("1");
        // Default policy on /accounts is independent — same IP unaffected.
        assertThat(invoke("/api/v1/accounts", "9.9.9.9").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Different IPs are tracked independently (port semantics)")
    void perKeyIsolation() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(invoke("/api/v1/x", "10.0.0.1").getStatus()).isEqualTo(200);
        }
        assertThat(invoke("/api/v1/x", "10.0.0.2").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Non-API URIs bypass the limiter entirely (probes, OAuth2, BFF metadata)")
    void nonApiBypasses() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/actuator/health");
        req.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        for (int i = 0; i < 100; i++) {
            filter.doFilter(req, res, chain);
        }
        verify(chain, times(100)).doFilter(org.mockito.Mockito.any(), org.mockito.Mockito.any());
        assertThat(limiter.consumeCount()).isZero();
    }

    private MockHttpServletResponse invoke(String uri, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI(uri);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        return res;
    }

    /** Deterministic per-(policy,key) counter — no clocks, no caches. */
    static final class FakeRateLimiter implements RateLimiter {
        private final java.util.Map<String, AtomicInteger> counts =
                new java.util.concurrent.ConcurrentHashMap<>();
        private final AtomicInteger consumeCount = new AtomicInteger();

        @Override
        public Decision tryConsume(String key, RateLimitPolicy policy) {
            consumeCount.incrementAndGet();
            String composed = policy.id() + ":" + key;
            int n = counts.computeIfAbsent(composed, k -> new AtomicInteger()).incrementAndGet();
            int limit = policy.requestsPerMinute();
            if (n > limit) return Decision.deny(limit, policy.windowSeconds());
            return Decision.allow(limit, limit - n, policy.windowSeconds());
        }

        int consumeCount() { return consumeCount.get(); }
    }

    /** Routes /api/v1/transfers/** to STRICT_POLICY, everything else to DEFAULT_POLICY. */
    static final class RoutingPolicyResolver implements BucketPolicyResolver {
        @Override
        public RateLimitPolicy resolve(HttpServletRequest request) {
            String uri = request.getRequestURI();
            if (uri != null && uri.startsWith("/api/v1/transfers")) return STRICT_POLICY;
            return DEFAULT_POLICY;
        }
    }
}



