package com.mariosmant.fintech.domain.event;

import com.mariosmant.fintech.domain.model.vo.TransferId;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a transfer has failed — terminal event.
 * The reason field describes what went wrong (fraud, insufficient funds, etc.).
 *
 * @author mariosmant
 * @since 1.0.0
 */
public record TransferFailedEvent(
        UUID eventId,
        String aggregateId,
        Instant occurredAt,
        TransferId transferId,
        String reason
) implements DomainEvent {
}

