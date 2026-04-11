package com.mariosmant.fintech.domain.event;

import com.mariosmant.fintech.domain.model.vo.AccountId;
import com.mariosmant.fintech.domain.model.vo.Currency;
import com.mariosmant.fintech.domain.model.vo.TransferId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a transfer request has been accepted and persisted.
 * This is the first event in the Saga, triggering fraud detection.
 *
 * @author mariosmant
 * @since 1.0.0
 */
public record TransferInitiatedEvent(
        UUID eventId,
        String aggregateId,
        Instant occurredAt,
        TransferId transferId,
        AccountId sourceAccountId,
        AccountId targetAccountId,
        BigDecimal amount,
        Currency sourceCurrency,
        Currency targetCurrency,
        String idempotencyKey
) implements DomainEvent {
}

