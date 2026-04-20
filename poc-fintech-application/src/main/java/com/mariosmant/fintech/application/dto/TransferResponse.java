package com.mariosmant.fintech.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read-model DTO for Transfer data returned to the API consumer.
 *
 * <p>Both account UUIDs and their IBANs are exposed so that the UI can display
 * human-friendly IBANs (banking best practice) while the API keeps UUID as the
 * canonical identifier for follow-up calls.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public record TransferResponse(
        UUID id,
        String status,
        UUID sourceAccountId,
        String sourceIban,
        UUID targetAccountId,
        String targetIban,
        BigDecimal sourceAmount,
        String sourceCurrency,
        BigDecimal targetAmount,
        String targetCurrency,
        BigDecimal exchangeRate,
        String failureReason,
        String idempotencyKey
) {
}
