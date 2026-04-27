package com.mariosmant.fintech.infrastructure.security.audit;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Actuator endpoint exposing the {@link AuditChainVerifier} report.
 *
 * <p>Registered at {@code /actuator/auditchain}. Exposure and role-based
 * access are gated by {@code management.endpoints.web.exposure.include}
 * and the Spring Security rules for the actuator group; this POC limits
 * the {@code /actuator/auditchain} URI to users holding {@code ROLE_ADMIN}
 * via {@code SecurityConfig.java}.</p>
 *
 * <h2>Operational use</h2>
 * <ul>
 *   <li>Called periodically by an external monitor (Prometheus blackbox,
 *       cron + curl) to surface tampering within minutes of occurrence.</li>
 *   <li>Provides machine-readable output — the caller inspects
 *       {@code valid} / {@code firstBadSeq} to drive alerts.</li>
 * </ul>
 *
 * <h3>Response shape</h3>
 * <pre>
 * {
 *   "valid": true,
 *   "rowsVerified": 12345,
 *   "elapsedMs": 120,
 *   "firstBadSeq": null,
 *   "message": "OK",
 *   "usingDevKey": false
 * }
 * </pre>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Component
@Endpoint(id = "auditchain")
public class AuditChainEndpoint {

    private final AuditChainVerifier verifier;
    private final AuditChainHasher hasher;

    public AuditChainEndpoint(AuditChainVerifier verifier, AuditChainHasher hasher) {
        this.verifier = verifier;
        this.hasher = hasher;
    }

    @ReadOperation
    public Map<String, Object> verify() {
        AuditChainVerifier.Report report = verifier.verifyAll();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("valid", report.valid());
        out.put("rowsVerified", report.rowsVerified());
        out.put("elapsedMs", report.elapsedMs());
        out.put("firstBadSeq", report.firstBadSeq());
        out.put("message", report.message());
        out.put("usingDevKey", hasher.isUsingFallback());
        return out;
    }
}

