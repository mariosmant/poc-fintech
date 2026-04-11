package com.mariosmant.fintech.domain.event;

import com.mariosmant.fintech.domain.model.vo.TransferId;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a transfer has completed successfully — terminal event.
 *
 * @author mariosmant
 * @since 1.0.0
 */
public record TransferCompletedEvent(
        UUID eventId,
        String aggregateId,
        Instant occurredAt,
        TransferId transferId
) implements DomainEvent {
}

