package com.mariosmant.fintech.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Base interface for all domain events in the fintech bounded context.
 *
 * <p>Every domain event carries a unique ID (for idempotent processing),
 * the aggregate ID that produced it, and a timestamp. Events are immutable
 * records published via the Transactional Outbox pattern.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public sealed interface DomainEvent
        permits TransferInitiatedEvent, FraudCheckCompletedEvent,
        FxConversionCompletedEvent, AccountDebitedEvent, AccountCreditedEvent,
        LedgerEntryRecordedEvent, TransferCompletedEvent, TransferFailedEvent {

    /** Unique event identifier for exactly-once processing. */
    UUID eventId();

    /** The aggregate root ID that produced this event. */
    String aggregateId();

    /** When this event occurred. */
    Instant occurredAt();
}

