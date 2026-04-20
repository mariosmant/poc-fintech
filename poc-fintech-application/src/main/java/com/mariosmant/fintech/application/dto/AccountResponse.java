package com.mariosmant.fintech.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read-model DTO for Account data returned to the API consumer.
 *
 * <p>Carries both the internal {@code id} (UUID — used by the API & persistence)
 * and the human-friendly {@code iban} (ISO 13616 — primary identifier in banking UIs).</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public record AccountResponse(
        UUID id,
        String iban,
        String ownerName,
        BigDecimal balance,
        String currency
) {
}
