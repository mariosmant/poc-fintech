package com.mariosmant.fintech.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-model DTO for double-entry ledger entries.
 *
 * @author mariosmant
 * @since 1.0.0
 */
public record LedgerEntryResponse(
        UUID id,
        UUID debitAccountId,
        UUID creditAccountId,
        BigDecimal amount,
        String currency,
        UUID transferId,
        Instant createdAt
) {
}

