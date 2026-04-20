package com.mariosmant.fintech.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * MDC Logging Filter — enriches every request's logging context with:
 * <ul>
 *   <li>{@code userId} — extracted from the JWT {@code sub} claim (Keycloak user ID)</li>
 *   <li>{@code username} — extracted from the JWT {@code preferred_username} claim</li>
 *   <li>{@code requestId} — unique per request (from X-Request-ID header or generated)</li>
 *   <li>{@code traceId} — from X-B3-TraceId header if available (OpenTelemetry)</li>
 * </ul>
 *
 * <p>Clears MDC in {@code finally} to prevent context leakage between requests
 * in thread-pooled environments (NIST AU-3, SOC 2 CC7.2).</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MdcLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // Request ID — from header or generate
            String requestId = request.getHeader("X-Request-ID");
            if (requestId == null || requestId.isBlank()) {
                requestId = UUID.randomUUID().toString();
            }
            MDC.put("requestId", requestId);
            response.setHeader("X-Request-ID", requestId);

            // Trace ID (OpenTelemetry)
            String traceId = request.getHeader("X-B3-TraceId");
            if (traceId != null && !traceId.isBlank()) {
                MDC.put("traceId", traceId);
            }

            // User ID from JWT (populated after Spring Security filter chain)
            populateUserContext();

            filterChain.doFilter(request, response);

            // Re-populate after security filter chain has run (for response logging)
            populateUserContext();

        } finally {
            MDC.clear();
        }
    }

    private void populateUserContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            MDC.put("userId", jwt.getSubject());
            String preferredUsername = jwt.getClaimAsString("preferred_username");
            if (preferredUsername != null) {
                MDC.put("username", preferredUsername);
            }
        }
    }
}

