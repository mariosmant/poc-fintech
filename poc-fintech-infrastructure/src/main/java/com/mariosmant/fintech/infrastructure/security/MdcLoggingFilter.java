package com.mariosmant.fintech.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * MDC logging filter — enriches every request's logging context with correlation
 * metadata that is surfaced by structured (JSON) log output and the MDC-aware
 * console pattern.
 *
 * <p><b>MDC keys populated:</b></p>
 * <ul>
 *   <li>{@code requestId} — unique per HTTP request. Sourced from the
 *       {@code X-Request-ID} request header when present and well-formed,
 *       otherwise a fresh UUIDv4. Mirrored back on the response so clients
 *       can correlate.</li>
 *   <li>{@code traceId} / {@code spanId} — extracted from the W3C
 *       <a href="https://www.w3.org/TR/trace-context/">Trace Context</a>
 *       {@code traceparent} request header (format
 *       {@code <version>-<trace-id>-<parent-id>-<flags>}). Only written if
 *       Micrometer Tracing has not already populated the keys, so an active
 *       OpenTelemetry scope always wins.</li>
 *   <li>{@code userId} — from the JWT {@code sub} claim (Keycloak user ID),
 *       populated once {@link SecurityContextHolder} is available.</li>
 *   <li>{@code username} — from the JWT {@code preferred_username} claim.</li>
 * </ul>
 *
 * <p>The MDC is cleared in {@code finally} to prevent context leakage between
 * requests in thread-pooled containers (NIST AU-3, SOC 2 CC7.2).</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MdcLoggingFilter extends OncePerRequestFilter {

    // Visible for tests
    static final String MDC_REQUEST_ID = "requestId";
    static final String MDC_TRACE_ID = "traceId";
    static final String MDC_SPAN_ID = "spanId";
    static final String MDC_USER_ID = "userId";
    static final String MDC_USERNAME = "username";

    static final String HEADER_REQUEST_ID = "X-Request-ID";
    static final String HEADER_TRACEPARENT = "traceparent";

    /**
     * W3C traceparent format: {@code <version>-<trace-id 32-hex>-<parent-id 16-hex>-<flags 2-hex>}.
     * All fields lowercase hex per the spec; we accept mixed-case and
     * normalise to lowercase when writing to MDC.
     */
    private static final Pattern TRACEPARENT =
            Pattern.compile("^[0-9a-fA-F]{2}-([0-9a-fA-F]{32})-([0-9a-fA-F]{16})-[0-9a-fA-F]{2}$");

    /**
     * Defence-in-depth cap on {@code X-Request-ID}: reject anything unreasonably
     * long or containing characters that have no business in a correlation id
     * (log-injection mitigation — OWASP A09).
     */
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String requestId = sanitiseRequestId(request.getHeader(HEADER_REQUEST_ID));
            MDC.put(MDC_REQUEST_ID, requestId);
            response.setHeader(HEADER_REQUEST_ID, requestId);

            populateTraceContext(request.getHeader(HEADER_TRACEPARENT));
            populateUserContext();

            filterChain.doFilter(request, response);

            // Re-populate after security filter chain has authenticated the request,
            // so any post-chain logging (access logs, @ControllerAdvice) still sees userId.
            populateUserContext();

        } finally {
            MDC.clear();
        }
    }

    /**
     * Accept a caller-supplied request-id when it passes {@link #SAFE_REQUEST_ID};
     * otherwise generate a fresh UUIDv4. This prevents log-injection via
     * newline / control-character smuggling in the header.
     */
    static String sanitiseRequestId(String headerValue) {
        if (headerValue != null && SAFE_REQUEST_ID.matcher(headerValue).matches()) {
            return headerValue;
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Extract W3C {@code traceparent} trace-id / span-id. Silently no-ops on any
     * malformed input (per spec — an invalid traceparent must be treated as
     * "no trace context received"). Does not overwrite values already populated
     * by Micrometer Tracing, so an active OTel scope always wins.
     */
    static void populateTraceContext(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return;
        }
        var m = TRACEPARENT.matcher(traceparent.trim());
        if (!m.matches()) {
            return;
        }
        if (MDC.get(MDC_TRACE_ID) == null) {
            MDC.put(MDC_TRACE_ID, m.group(1).toLowerCase());
        }
        if (MDC.get(MDC_SPAN_ID) == null) {
            MDC.put(MDC_SPAN_ID, m.group(2).toLowerCase());
        }
    }

    private static void populateUserContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            MDC.put(MDC_USER_ID, jwt.getSubject());
            String preferredUsername = jwt.getClaimAsString("preferred_username");
            if (preferredUsername != null) {
                MDC.put(MDC_USERNAME, preferredUsername);
            }
        }
    }
}
