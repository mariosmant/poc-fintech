package com.mariosmant.fintech.application.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents an event in the Transactional Outbox table.
 *
 * <p>Events are written atomically in the same DB transaction as the
 * aggregate state change. A background poller then publishes them to
 * Kafka, ensuring <b>at-least-once delivery</b>. Combined with an
 * idempotent consumer, this achieves <b>exactly-once processing</b>.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public class OutboxEvent {

    private UUID id;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String payload;
    private boolean published;
    private Instant createdAt;

    /** Default constructor for JPA / framework use. */
    public OutboxEvent() {
        // Empty constructor.
    }

    /**
     * Full constructor.
     */
    public OutboxEvent(UUID id, String aggregateType, String aggregateId,
                       String eventType, String payload, boolean published,
                       Instant createdAt) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.published = published;
        this.createdAt = createdAt;
    }

    /**
     * Factory: creates a new unpublished outbox event.
     */
    public static OutboxEvent create(String aggregateType, String aggregateId,
                                     String eventType, String payload) {
        return new OutboxEvent(UUID.randomUUID(), aggregateType, aggregateId,
                eventType, payload, false, Instant.now());
    }

    // ── Getters & Setters ────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

