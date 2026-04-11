package com.mariosmant.fintech.domain.event;

import com.mariosmant.fintech.domain.model.vo.Currency;
import com.mariosmant.fintech.domain.model.vo.TransferId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Raised when FX conversion is completed and the target amount is known.
 *
 * @author mariosmant
 * @since 1.0.0
 */
public record FxConversionCompletedEvent(
        UUID eventId,
        String aggregateId,
        Instant occurredAt,
        TransferId transferId,
        BigDecimal exchangeRate,
        BigDecimal convertedAmount,
        Currency targetCurrency
) implements DomainEvent {
}

