package com.mariosmant.fintech.domain.event;

import com.mariosmant.fintech.domain.model.vo.AccountId;
import com.mariosmant.fintech.domain.model.vo.TransferId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Raised when an account has been successfully credited.
 *
 * @author mariosmant
 * @since 1.0.0
 */
public record AccountCreditedEvent(
        UUID eventId,
        String aggregateId,
        Instant occurredAt,
        AccountId accountId,
        TransferId transferId,
        BigDecimal amount
) implements DomainEvent {
}

