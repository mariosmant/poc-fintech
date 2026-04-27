package com.mariosmant.fintech.infrastructure.security.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * AOP Aspect that intercepts methods annotated with {@link Audited} and
 * persists an audit trail to the {@code audit_log} table <b>through the
 * HMAC tamper-evidence chain</b> ({@link AuditChainWriter}).
 *
 * <h2>Captured fields</h2>
 * <p>user ID, username, action, resource type, IP address, HTTP method,
 * request URI, response status, duration.</p>
 *
 * <h2>Architecture notes</h2>
 * <ul>
 *   <li><b>Cross-cutting concern, not business logic</b> — realised as a
 *       Spring AOP {@code @Around} advice so audit responsibility never
 *       pollutes the controllers / use cases (OWASP ASVS V7.1.1).</li>
 *   <li><b>Best-effort writes</b> — a failure in the audit path must not
 *       roll back the business method. The chain writer uses a separate
 *       {@code REQUIRES_NEW} transaction (see {@link AuditChainWriter}).</li>
 *   <li><b>Tamper evidence</b> — the writer computes a row HMAC-SHA256 over
 *       {@code (prev_hash || chain_seq || canonical(row))}. Verified by
 *       {@link AuditChainVerifier} on demand (actuator endpoint).</li>
 * </ul>
 *
 * <h2>Standards mapping</h2>
 * <p>NIST AU-2 / AU-9(3) / AU-10 · SOC 2 CC7.2 · PCI DSS v4.0.1 §10.2 / §10.5.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditChainWriter chainWriter;

    public AuditAspect(AuditChainWriter chainWriter) {
        this.chainWriter = chainWriter;
    }

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        long start = System.currentTimeMillis();
        String userId = "anonymous";
        String username = "anonymous";
        String ipAddress = "unknown";
        String httpMethod = "unknown";
        String requestUri = "unknown";

        // Extract request context
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                ipAddress = extractClientIp(request);
                httpMethod = request.getMethod();
                requestUri = request.getRequestURI();
            }
        } catch (Exception ignored) {
        }

        // Extract user from JWT
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                userId = jwt.getSubject();
                String pref = jwt.getClaimAsString("preferred_username");
                if (pref != null) username = pref;
            }
        } catch (Exception ignored) {
        }

        Object result;
        Integer responseStatus = null;
        String resourceId = null;
        try {
            result = joinPoint.proceed();

            // Extract response status and resource ID from ResponseEntity
            if (result instanceof ResponseEntity<?> re) {
                responseStatus = re.getStatusCode().value();
                Object body = re.getBody();
                if (body != null) {
                    resourceId = extractResourceId(body);
                }
            }

            return result;
        } catch (Throwable ex) {
            responseStatus = 500;
            throw ex;
        } finally {
            long durationMs = System.currentTimeMillis() - start;

            try {
                var auditLog = new AuditLogEntity(
                        UUID.randomUUID(),
                        userId,
                        username,
                        audited.action(),
                        audited.resourceType(),
                        resourceId,
                        null,
                        ipAddress,
                        httpMethod,
                        requestUri,
                        responseStatus,
                        durationMs
                );
                // Route through the HMAC chain writer — same table, chained row_hash.
                chainWriter.append(auditLog);

                log.info("AUDIT: action={}, user={}, resource={}/{}, ip={}, status={}, duration={}ms",
                        audited.action(), username, audited.resourceType(), resourceId,
                        ipAddress, responseStatus, durationMs);
            } catch (Exception ex) {
                log.error("Failed to persist audit log for action={}", audited.action(), ex);
            }
        }
    }

    /**
     * Extracts the real client IP, considering proxy headers.
     * Validates against header injection.
     */
    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // Take the first IP (client IP) and validate format
            String clientIp = xff.split(",")[0].trim();
            if (clientIp.matches("^[0-9a-fA-F.:]+$")) {
                return clientIp;
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * Attempts to extract a resource ID from the response body via reflection.
     */
    private String extractResourceId(Object body) {
        try {
            var method = body.getClass().getMethod("id");
            Object id = method.invoke(body);
            return id != null ? id.toString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}

