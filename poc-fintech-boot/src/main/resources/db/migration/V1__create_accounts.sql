-- ============================================================================
-- V1: Create accounts table
-- Stores financial account data with optimistic locking (version column).
-- Uses NUMERIC(19,4) for monetary precision as per financial standards.
-- ============================================================================

CREATE TABLE accounts (
    id               UUID         PRIMARY KEY,
    owner_name       VARCHAR(255) NOT NULL,
    balance_amount   NUMERIC(19, 4) NOT NULL DEFAULT 0,
    balance_currency VARCHAR(3)   NOT NULL,
    version          INTEGER      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ
);

