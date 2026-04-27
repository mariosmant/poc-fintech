-- ============================================================================
-- V11: HMAC key rotation for the tamper-evident audit chain
--      (PCI DSS v4.0.1 §3.7 key lifecycle, NIST SP 800-57 §5, NIST AU-9(3))
--
-- Why
-- ---
-- V10 installed a per-row HMAC-SHA256 chain bound to a single secret key. That
-- key cannot be retired in place: rotating it invalidates every historical
-- row_hash and turns the verifier permanently red. PCI DSS §3.7 (and NIST
-- SP 800-57 Part 1 §5) require a documented cryptoperiod with planned rotation
-- and an emergency-rotation path on suspected compromise.
--
-- This migration adds a `key_id` discriminator to every audit row and an
-- `active_key_id` pointer on the head row. The verifier looks up the right
-- key per row via {@code AuditChainKeyRing}, so rows signed under key_id=1
-- still verify after a rotation that flips the active key to id=2.
--
-- Schema
-- ------
--   audit_log.key_id           SMALLINT NOT NULL — id of the key that signed
--                              this row's row_hash.
--   audit_log_head.active_key_id SMALLINT NOT NULL — id of the key currently
--                              used for new appends.
--
-- Backfill safety vs V9 immutability
-- ----------------------------------
-- V9 installs BEFORE UPDATE triggers on audit_log that abort any UPDATE.
-- PostgreSQL's `ALTER TABLE ... ADD COLUMN ... DEFAULT ... NOT NULL` does
-- NOT fire row-level UPDATE triggers (since PG 11 the default is stored in
-- pg_attribute and read at scan time — no rewrite, no trigger fire). So the
-- backfill is silent and trigger-safe. Verified against V9 in
-- AuditChainIntegrationTest.
--
-- Rotation runbook (operational)
-- ------------------------------
-- 1. Generate a new 32-byte random key in your KMS / Vault.
-- 2. Add it to the application keyring at the next available id (env or DB).
-- 3. Restart all instances — they now know about both keys.
-- 4. Flip `app.audit.chain.active-key-id` to the new id and restart.
-- 5. New rows are signed with the new key; old rows continue to verify.
-- 6. Retire the old key from the ring after retention expiry (PCI DSS §10.7).
--
-- Standards mapping
-- -----------------
-- PCI DSS v4.0.1 §3.7        — key lifecycle: change of cryptographic keys
-- NIST SP 800-57 Part 1 §5   — key management transitions
-- NIST SP 800-53 AU-9(3)     — cryptographic protection of audit information
-- ISO/IEC 27001 A.10.1.2     — key management
-- ============================================================================

-- 1. Add discriminator with default 1 (the implicit key from V10).
--    Bigint is overkill; SMALLINT (-32768..32767) is plenty for human-readable ids.
ALTER TABLE audit_log
    ADD COLUMN IF NOT EXISTS key_id SMALLINT NOT NULL DEFAULT 1;

ALTER TABLE audit_log_head
    ADD COLUMN IF NOT EXISTS active_key_id SMALLINT NOT NULL DEFAULT 1;

-- Index supports verifier paging by (chain_seq, key_id) when rotated segments
-- need targeted re-verification.
CREATE INDEX IF NOT EXISTS idx_audit_log_key_id ON audit_log(key_id);

COMMENT ON COLUMN audit_log.key_id IS
    'Id of the HMAC key that produced row_hash. Looked up via AuditChainKeyRing '
    'at verify time. Old rows keep their original key_id forever.';

COMMENT ON COLUMN audit_log_head.active_key_id IS
    'Id of the HMAC key currently used by AuditChainWriter for new appends. '
    'Bumped at rotation time; older keys remain in the ring for verification.';

