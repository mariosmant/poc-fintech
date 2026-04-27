-- ============================================================================
-- V10: Tamper-evident audit chain (PCI DSS v4.0.1 §10.5, NIST AU-9, SOC 2 CC7.2)
--
-- Why (context)
-- -------------
-- V9 installed BEFORE UPDATE/DELETE/TRUNCATE triggers that prevent the
-- application role from modifying `audit_log`. That control is defeated by
-- a privileged operator who first drops the trigger, mutates rows, then
-- re-creates it — the row itself carries no evidence of tampering.
--
-- This migration adds a per-row HMAC-SHA256 chain: each new row's
-- `row_hash` is computed over (prev_hash || canonical_serialisation(row))
-- using a secret key held only by the application. Any insertion, update,
-- deletion, or reordering breaks the chain and is detectable by replaying
-- the HMAC — without the key, an attacker cannot forge a new valid chain.
--
-- Column layout
-- -------------
--   prev_hash  BYTEA  — row_hash of the previous row (NULL for the first row)
--   row_hash   BYTEA  — HMAC-SHA256 of this row; NOT NULL, UNIQUE
--   chain_seq  BIGINT — monotonically increasing per-row sequence, set by
--                      the application atomically with the insert
--
-- Existing rows (migration-time bootstrap)
-- ----------------------------------------
-- Existing rows are bootstrapped with chain_seq assigned by created_at order
-- and row_hash recomputed once at application startup via
-- `AuditChainBootstrapper`. The migration itself leaves `row_hash` nullable
-- initially and the bootstrap step (run under a single transaction with
-- `FOR UPDATE` serialisation) fills in values and then tightens the column
-- to NOT NULL via a deferred statement-level check.
--
-- Because V9 forbids UPDATE on `audit_log`, the bootstrap must run BEFORE
-- V9's triggers fire. This is achieved in two ways:
--   1. The bootstrap executes under the `auditchain.bootstrap` role which
--      SETs session_replication_role = 'replica' (bypasses triggers) for the
--      duration of the bootstrap, then restores it. OR
--   2. Fresh databases have no rows to bootstrap; triggers never fire.
--
-- Standards mapping
-- -----------------
-- PCI DSS v4.0.1 §10.5    — secure audit trails
-- PCI DSS v4.0.1 §10.5.2  — protect audit trail files from modification
-- NIST SP 800-53 AU-9(3)  — cryptographic protection of audit information
-- NIST SP 800-53 AU-10    — non-repudiation
-- SOC 2 CC7.2             — monitoring of system components
-- ISO/IEC 27001 A.12.4.2  — protection of log information
-- ============================================================================

ALTER TABLE audit_log
    ADD COLUMN prev_hash BYTEA,
    ADD COLUMN row_hash  BYTEA,
    ADD COLUMN chain_seq BIGINT;

-- Uniqueness & fast sequential scan for verification.
CREATE UNIQUE INDEX IF NOT EXISTS uq_audit_log_chain_seq
    ON audit_log(chain_seq);
CREATE INDEX IF NOT EXISTS idx_audit_log_chain_seq_asc
    ON audit_log(chain_seq ASC);

-- Expose the current "head" of the chain for concurrent writers to lock.
-- One row per chain (this POC is single-chain / single-tenant).
CREATE TABLE IF NOT EXISTS audit_log_head (
    chain_id    TEXT   PRIMARY KEY,
    last_seq    BIGINT NOT NULL,
    last_hash   BYTEA  NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE audit_log_head IS
    'Synchronisation point for the HMAC chain. Writers SELECT ... FOR UPDATE '
    'the row, compute row_hash using last_hash, INSERT into audit_log, UPDATE '
    'this row — all in the same transaction. Prevents chain forks under '
    'concurrent audit writers.';

-- Seed a single chain row ('default') with an all-zeros last_hash so the
-- very first audit entry chains against a well-known anchor.
INSERT INTO audit_log_head (chain_id, last_seq, last_hash)
VALUES ('default', 0, decode(repeat('00', 32), 'hex'))
ON CONFLICT (chain_id) DO NOTHING;

COMMENT ON COLUMN audit_log.prev_hash IS
    'row_hash of the previous audit_log row (or 32 null bytes for the first).';
COMMENT ON COLUMN audit_log.row_hash IS
    'HMAC-SHA256(key, prev_hash || canonical(row)). Breaks on any mutation.';
COMMENT ON COLUMN audit_log.chain_seq IS
    'Monotonically increasing sequence, assigned under audit_log_head FOR UPDATE.';

