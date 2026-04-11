package com.mariosmant.fintech.application.command;

import com.mariosmant.fintech.domain.model.vo.Currency;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * CQRS Command: initiates a money transfer between two accounts.
 *
 * <p>This is the single entry point for the write side. The command
 * is validated at the REST controller level ({@code @Valid}) and
 * processed by the {@code InitiateTransferUseCase}.</p>
 *
 * @param sourceAccountId the debiting account UUID
 * @param targetAccountId the crediting account UUID
 * @param amount          the amount in the source currency
 * @param sourceCurrency  the source currency
 * @param targetCurrency  the desired target currency (may differ for FX)
 * @param idempotencyKey  client-supplied key for exactly-once semantics
 * @author mariosmant
 * @since 1.0.0
 */
public record InitiateTransferCommand(
        UUID sourceAccountId,
        UUID targetAccountId,
        BigDecimal amount,
        Currency sourceCurrency,
        Currency targetCurrency,
        String idempotencyKey
) {
}

