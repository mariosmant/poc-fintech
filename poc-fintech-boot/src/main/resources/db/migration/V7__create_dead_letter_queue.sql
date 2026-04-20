-- ============================================================================
-- V7: Create dead_letter_queue table
-- Persists Kafka messages that failed processing after all retries.
-- Enables manual inspection and replay of failed messages.
-- ============================================================================

CREATE TABLE dead_letter_queue (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    topic            VARCHAR(255) NOT NULL,
    partition_num    INTEGER,
    offset_num       BIGINT,
    key              VARCHAR(255),
    payload          TEXT         NOT NULL,
    error_message    TEXT,
    exception_class  VARCHAR(500),
    retry_count      INTEGER      NOT NULL DEFAULT 0,
    resolved         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    resolved_at      TIMESTAMPTZ
);

CREATE INDEX idx_dlq_resolved ON dead_letter_queue(resolved);
CREATE INDEX idx_dlq_topic ON dead_letter_queue(topic);
CREATE INDEX idx_dlq_created_at ON dead_letter_queue(created_at);
