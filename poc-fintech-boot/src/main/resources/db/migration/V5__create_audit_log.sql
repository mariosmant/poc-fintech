-- ============================================================================
-- V5: Create audit_log table
-- Stores security audit trail for compliance (NIST AU-2, SOC 2 CC7.2).
-- All critical user actions are recorded with user ID, IP, timestamps.
-- ============================================================================

CREATE TABLE audit_log (
    id               UUID         PRIMARY KEY,
    user_id          VARCHAR(255) NOT NULL,
    username         VARCHAR(255),
    action           VARCHAR(100) NOT NULL,
    resource_type    VARCHAR(100),
    resource_id      VARCHAR(255),
    details          TEXT,
    ip_address       VARCHAR(45),
    http_method      VARCHAR(10),
    request_uri      VARCHAR(2048),
    response_status  INTEGER,
    duration_ms      BIGINT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_user_id ON audit_log(user_id);
CREATE INDEX idx_audit_log_action ON audit_log(action);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at);
CREATE INDEX idx_audit_log_resource ON audit_log(resource_type, resource_id);

