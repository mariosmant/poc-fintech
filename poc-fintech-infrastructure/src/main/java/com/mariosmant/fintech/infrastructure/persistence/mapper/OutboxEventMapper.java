package com.mariosmant.fintech.infrastructure.persistence.mapper;

import com.mariosmant.fintech.application.outbox.OutboxEvent;
import com.mariosmant.fintech.infrastructure.persistence.entity.OutboxEventJpaEntity;

/**
 * Maps between {@link OutboxEvent} (application) and {@link OutboxEventJpaEntity} (JPA).
 *
 * @author mariosmant
 * @since 1.0.0
 */
public final class OutboxEventMapper {

    private OutboxEventMapper() { /* utility */ }

    public static OutboxEvent toDomain(OutboxEventJpaEntity e) {
        return new OutboxEvent(e.getId(), e.getAggregateType(), e.getAggregateId(),
                e.getEventType(), e.getPayload(), e.isPublished(), e.getCreatedAt());
    }

    public static OutboxEventJpaEntity toEntity(OutboxEvent o) {
        OutboxEventJpaEntity e = new OutboxEventJpaEntity();
        e.setId(o.getId());
        e.setAggregateType(o.getAggregateType());
        e.setAggregateId(o.getAggregateId());
        e.setEventType(o.getEventType());
        e.setPayload(o.getPayload());
        e.setPublished(o.isPublished());
        e.setCreatedAt(o.getCreatedAt());
        return e;
    }
}

