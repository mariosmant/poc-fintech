package com.mariosmant.fintech.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-model DTO for Transfer data returned to the API consumer.
 *
 * @author mariosmant
 * @since 1.0.0
 */
public record TransferResponse(
        UUID id,
        String status,
        UUID sourceAccountId,
        UUID targetAccountId,
        BigDecimal sourceAmount,
        String sourceCurrency,
        BigDecimal targetAmount,
        String targetCurrency,
        BigDecimal exchangeRate,
        String failureReason,
        String idempotencyKey
) {
}

