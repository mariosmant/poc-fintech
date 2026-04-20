package com.mariosmant.fintech.infrastructure.web.dto;

import com.mariosmant.fintech.domain.model.vo.Currency;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * REST request body for initiating a transfer.
 *
 * <p>Exactly one of {@code targetAccountId} or {@code targetIban} must be supplied:
 * UUID for transfers between the authenticated user's own accounts, IBAN for
 * third-party beneficiaries. Validated using Jakarta Bean Validation
 * (OWASP input validation).</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public record InitiateTransferRequest(
        @NotNull(message = "Source account ID is required")
        UUID sourceAccountId,

        UUID targetAccountId,

        @Pattern(
                regexp = "^\\s*[A-Za-z]{2}\\d{2}[A-Za-z0-9\\s]{11,34}\\s*$",
                message = "Target IBAN is malformed")
        String targetIban,

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
    /** Exactly-one-of constraint for target identification. */
    @JsonIgnore
    @AssertTrue(message = "Exactly one of targetAccountId or targetIban must be provided")
    public boolean isTargetProvidedExactlyOnce() {
        boolean hasId = targetAccountId != null;
        boolean hasIban = targetIban != null && !targetIban.isBlank();
        return hasId ^ hasIban;
    }
}

