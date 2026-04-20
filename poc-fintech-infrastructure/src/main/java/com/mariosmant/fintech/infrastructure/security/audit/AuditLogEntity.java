package com.mariosmant.fintech.infrastructure.security.audit;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code audit_log} table.
 * Captures all security-relevant actions for compliance (NIST AU-2, SOC 2 CC7.2).
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Entity
@Table(name = "audit_log")
@EntityListeners(AuditingEntityListener.class)
public class AuditLogEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "username")
    private String username;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "http_method")
    private String httpMethod;

    @Column(name = "request_uri")
    private String requestUri;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "duration_ms")
    private Long durationMs;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLogEntity() {}

    public AuditLogEntity(UUID id, String userId, String username, String action,
                          String resourceType, String resourceId, String details,
                          String ipAddress, String httpMethod, String requestUri,
                          Integer responseStatus, Long durationMs) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.details = details;
        this.ipAddress = ipAddress;
        this.httpMethod = httpMethod;
        this.requestUri = requestUri;
        this.responseStatus = responseStatus;
        this.durationMs = durationMs;
    }

    // Getters
    public UUID getId() { return id; }
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getDetails() { return details; }
    public String getIpAddress() { return ipAddress; }
    public String getHttpMethod() { return httpMethod; }
    public String getRequestUri() { return requestUri; }
    public Integer getResponseStatus() { return responseStatus; }
    public Long getDurationMs() { return durationMs; }
    public Instant getCreatedAt() { return createdAt; }
}

