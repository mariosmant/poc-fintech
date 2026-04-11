package com.mariosmant.fintech.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read-model DTO for Account data returned to the API consumer.
 *
 * @author mariosmant
 * @since 1.0.0
 */
public record AccountResponse(
        UUID id,
        String ownerName,
        BigDecimal balance,
        String currency
) {
}

