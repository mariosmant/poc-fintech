-- ============================================================================
-- V4: Create outbox_events table (Transactional Outbox pattern)
-- Events are written atomically with aggregate state changes and
-- published to Kafka by a background poller.
-- ============================================================================

CREATE TABLE outbox_events (
    id             UUID        PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id   VARCHAR(50) NOT NULL,
    event_type     VARCHAR(50) NOT NULL,
    payload        TEXT        NOT NULL,
    published      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbox_unpublished ON outbox_events(published, created_at)
    WHERE published = FALSE;

