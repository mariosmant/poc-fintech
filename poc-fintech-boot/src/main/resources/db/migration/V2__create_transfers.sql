-- ============================================================================
-- V2: Create transfers table
-- Tracks money transfer lifecycle with saga state and optimistic locking.
-- Unique index on idempotency_key ensures exactly-once request processing.
-- ============================================================================

CREATE TABLE transfers (
    id                UUID           PRIMARY KEY,
    source_account_id UUID           NOT NULL REFERENCES accounts(id),
    target_account_id UUID           NOT NULL REFERENCES accounts(id),
    source_amount     NUMERIC(19, 4) NOT NULL,
    source_currency   VARCHAR(3)     NOT NULL,
    target_amount     NUMERIC(19, 4),
    target_currency   VARCHAR(3)     NOT NULL,
    exchange_rate     NUMERIC(19, 8),
    status            VARCHAR(20)    NOT NULL,
    idempotency_key   VARCHAR(255)   NOT NULL,
    failure_reason    TEXT,
    version           INTEGER        NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_transfers_idempotency_key ON transfers(idempotency_key);
CREATE INDEX idx_transfers_status ON transfers(status);

