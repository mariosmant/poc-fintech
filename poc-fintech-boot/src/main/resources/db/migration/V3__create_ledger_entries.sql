-- ============================================================================
-- V3: Create ledger_entries table (double-entry accounting)
-- Each row represents one side of a balanced debit/credit pair.
-- ============================================================================

CREATE TABLE ledger_entries (
    id                UUID           PRIMARY KEY,
    debit_account_id  UUID           NOT NULL REFERENCES accounts(id),
    credit_account_id UUID           NOT NULL REFERENCES accounts(id),
    amount            NUMERIC(19, 4) NOT NULL,
    currency          VARCHAR(3)     NOT NULL,
    transfer_id       UUID           NOT NULL REFERENCES transfers(id),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ledger_transfer_id ON ledger_entries(transfer_id);
CREATE INDEX idx_ledger_debit_account ON ledger_entries(debit_account_id);
CREATE INDEX idx_ledger_credit_account ON ledger_entries(credit_account_id);

