package com.mariosmant.fintech.domain.event;

import com.mariosmant.fintech.domain.model.vo.TransferId;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when the fraud detection check completes (approved or rejected).
 *
 * @author mariosmant
 * @since 1.0.0
 */
public record FraudCheckCompletedEvent(
        UUID eventId,
        String aggregateId,
        Instant occurredAt,
        TransferId transferId,
        boolean approved,
        String reason,
        int riskScore
) implements DomainEvent {
}

