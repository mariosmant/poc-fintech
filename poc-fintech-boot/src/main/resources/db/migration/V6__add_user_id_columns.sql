-- ============================================================================
-- V6: Add owner_id to accounts, initiated_by to transfers
-- Links financial entities to authenticated Keycloak user IDs.
-- User ID comes from JWT subject claim — never from client input.
-- ============================================================================

ALTER TABLE accounts ADD COLUMN owner_id VARCHAR(255);
ALTER TABLE transfers ADD COLUMN initiated_by VARCHAR(255);

CREATE INDEX idx_accounts_owner_id ON accounts(owner_id);
CREATE INDEX idx_transfers_initiated_by ON transfers(initiated_by);

