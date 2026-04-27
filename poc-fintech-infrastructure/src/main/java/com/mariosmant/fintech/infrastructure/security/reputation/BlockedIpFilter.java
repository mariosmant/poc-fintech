package com.mariosmant.fintech.infrastructure.security.reputation;

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
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 *IP-reputation pre-filter.
 *
 * <p>Runs <b>before</b> {@code RateLimitFilter} so a noisy {@code /26} from
 * a known abuse network never gets to consume token buckets. Blocked
 * requests receive {@code 403 Forbidden} with an RFC-7807 problem+json
 * body keyed by the {@code urn:fintech:error:blocked-by-reputation}
 * canonical type.</p>
 *
 * <h2>Scope</h2>
 * <p>Only {@code /api/**} is filtered — health probes, OAuth2 redirects,
 * and BFF metadata endpoints stay reachable from any IP so an operator
 * watching a misconfigured feed can still load {@code /actuator/health}
 * to remediate. The filter is a no-op when
 * {@code app.security.ip-reputation.enabled=false} (default), so
 * single-deployment / single-tenant POCs don't carry the lookup cost.</p>
 *
 * <h2>Filter ordering</h2>
 * <p>Registered with order {@link org.springframework.core.Ordered#HIGHEST_PRECEDENCE}{@code + 100}
 * by {@code IpReputationConfig}, ensuring it runs before the bare-bean
 * registered {@code RateLimitFilter} (default order = LOWEST_PRECEDENCE).</p>
 *
 * <h2>Standards mapping</h2>
 * <ul>
 *   <li>NIST SP 800-53 Rev 5 — SC-7 Boundary Protection.</li>
 *   <li>OWASP API Security Top 10 — API4:2023 Unrestricted Resource Consumption.</li>
 *   <li>OWASP ASVS 4.0.3 — V11.1.4 (resource-consumption protection).</li>
 *   <li>RFC 7807 — Problem Details for HTTP APIs.</li>
 * </ul>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public class BlockedIpFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BlockedIpFilter.class);

    private final IpReputationService reputation;
    private final ObjectMapper objectMapper;

    public BlockedIpFilter(IpReputationService reputation, ObjectMapper objectMapper) {
        this.reputation = reputation;
        this.objectMapper = objectMapper;
        log.info("BlockedIpFilter — wired with reputation={} ({} entries)",
                reputation.getClass().getSimpleName(), reputation.size());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Only filter API endpoints — keep probes and OAuth2 paths reachable.
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }
        String addr = request.getRemoteAddr();
        if (reputation.isBlocked(addr)) {
            // INFO not WARN — this is the expected hot path under abuse;
            // WARN-level noise would drown out genuine incidents.
            log.info("Request blocked by IP reputation addr={} uri={}", addr, uri);
            writeBlockedResponse(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeBlockedResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ProblemDetail body = ProblemDetails.of(
                HttpStatus.FORBIDDEN,
                ProblemDetails.ErrorType.BLOCKED_BY_REPUTATION,
                "Your network address is on a curated abuse block-list. "
                        + "If you believe this is an error, contact support.");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}

