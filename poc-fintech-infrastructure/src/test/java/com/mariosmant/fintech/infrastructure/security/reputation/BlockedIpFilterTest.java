package com.mariosmant.fintech.infrastructure.security.reputation;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * verifies the BlockedIpFilter denies requests
 * from listed IPs with a 403 + RFC 7807 problem+json body, lets clean IPs
 * through, and skips non-API paths entirely.
 */
class BlockedIpFilterTest {

    private final CaffeineIpReputationService svc = new CaffeineIpReputationService();
    private final BlockedIpFilter filter = new BlockedIpFilter(svc, new ObjectMapper());

    @Test
    @DisplayName("Blocked IP on /api/** → 403 + problem+json + canonical type URN")
    void blockedIpReturns403() throws Exception {
        svc.replace(List.of("203.0.113.0/24"));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/transfers");
        req.setRemoteAddr("203.0.113.42");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentType()).startsWith("application/problem+json");
        String body = res.getContentAsString();
        assertThat(body).contains("\"type\":\"urn:fintech:error:blocked-by-reputation\"");
        assertThat(body).contains("\"status\":403");
        assertThat(body).contains("\"title\":\"Blocked by IP reputation\"");
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    @DisplayName("Clean IP on /api/** → forwarded to chain")
    void cleanIpForwarded() throws Exception {
        svc.replace(List.of("203.0.113.0/24"));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/transfers");
        req.setRemoteAddr("198.51.100.7");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        verify(chain, times(1)).doFilter(req, res);
    }

    @Test
    @DisplayName("Non-API path is forwarded even from a blocked IP (probes stay reachable)")
    void nonApiBypassesEvenForBlocked() throws Exception {
        svc.replace(List.of("203.0.113.0/24"));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/actuator/health");
        req.setRemoteAddr("203.0.113.42");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Empty reputation snapshot lets every API request through")
    void emptyReputationLetsAllThrough() throws Exception {
        // svc is constructed empty.
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/accounts");
        req.setRemoteAddr("203.0.113.42");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }
}

