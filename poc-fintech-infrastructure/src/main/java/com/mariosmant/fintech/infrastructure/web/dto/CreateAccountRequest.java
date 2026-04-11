package com.mariosmant.fintech.infrastructure.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * REST request body for creating an account.
 *
 * @author mariosmant
 * @since 1.0.0
 */
public record CreateAccountRequest(
        @NotBlank(message = "Owner name is required")
        String ownerName,

        @NotBlank(message = "Currency is required")
        String currency,

        @NotNull(message = "Initial balance is required")
        @DecimalMin(value = "0.00", message = "Balance must not be negative")
        BigDecimal initialBalance
) {
}

