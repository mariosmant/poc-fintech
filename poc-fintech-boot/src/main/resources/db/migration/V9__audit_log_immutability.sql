-- ============================================================================
-- V9: Audit-log immutability (PCI DSS v4.0.1 §10.3.4 / §10.5.2, NIST AU-9)
--
-- Purpose
-- -------
-- Enforce at the database level that rows in `audit_log` cannot be modified
-- or deleted by any role short of superuser. This protects the integrity of
-- the security audit trail against both application-layer bugs and insider
-- threats operating through the regular application database credentials.
--
-- Implementation
-- --------------
-- A BEFORE UPDATE / BEFORE DELETE trigger raises an EXCEPTION — which rolls
-- back the enclosing transaction — so no partial mutation can ever be
-- committed. The trigger is SECURITY DEFINER-free (owned by the same role as
-- the table) so a privilege-escalation attempt via schema manipulation still
-- trips the constraint.
--
-- Compensating controls
-- ---------------------
-- * Retention: application-layer purge jobs must use TRUNCATE on a *copy*
--   table (e.g. partition swap) rather than DELETE, so the trigger never
--   needs to be temporarily disabled in production.
-- * Tamper evidence: V10 (planned) will add a per-row HMAC chain column
--   linking each audit entry to the SHA3-256 of its predecessor, so any
--   successful bypass of this trigger is still detectable out-of-band.
--
-- Standards mapping
-- -----------------
-- PCI DSS v4.0.1 §10.3.4  — audit trails protected from modification
-- PCI DSS v4.0.1 §10.5.2  — audit log file integrity
-- NIST SP 800-53 AU-9     — protection of audit information
-- SOC 2 CC7.2             — monitoring of system components
-- ============================================================================

CREATE OR REPLACE FUNCTION audit_log_reject_mutation()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        'audit_log rows are immutable (PCI DSS 10.3.4 / NIST AU-9); operation % rejected',
        TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

-- Reject UPDATEs
DROP TRIGGER IF EXISTS trg_audit_log_no_update ON audit_log;
CREATE TRIGGER trg_audit_log_no_update
    BEFORE UPDATE ON audit_log
    FOR EACH ROW
    EXECUTE FUNCTION audit_log_reject_mutation();

-- Reject DELETEs
DROP TRIGGER IF EXISTS trg_audit_log_no_delete ON audit_log;
CREATE TRIGGER trg_audit_log_no_delete
    BEFORE DELETE ON audit_log
    FOR EACH ROW
    EXECUTE FUNCTION audit_log_reject_mutation();

-- Reject TRUNCATE (trigger fires once per statement, not per row)
DROP TRIGGER IF EXISTS trg_audit_log_no_truncate ON audit_log;
CREATE TRIGGER trg_audit_log_no_truncate
    BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT
    EXECUTE FUNCTION audit_log_reject_mutation();

-- Documentation on the table itself — surfaces in \d+ audit_log
COMMENT ON TABLE audit_log IS
    'Security audit trail (PCI DSS 10.3.4 / NIST AU-9 / SOC 2 CC7.2). '
    'Append-only; UPDATE / DELETE / TRUNCATE are rejected by triggers.';

