package com.mariosmant.fintech.infrastructure.web.dto;

import com.mariosmant.fintech.domain.model.vo.Currency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * REST request body for initiating a transfer.
 *
 * <p>Validated using Jakarta Bean Validation constraints
 * to prevent injection of invalid data (OWASP input validation).</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public record InitiateTransferRequest(
        @NotNull(message = "Source account ID is required")
        UUID sourceAccountId,

        @NotNull(message = "Target account ID is required")
        UUID targetAccountId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be positive")
        BigDecimal amount,

        @NotNull(message = "Source currency is required")
        Currency sourceCurrency,

        @NotNull(message = "Target currency is required")
        Currency targetCurrency,

        @NotBlank(message = "Idempotency key is required")
        String idempotencyKey
) {
}

