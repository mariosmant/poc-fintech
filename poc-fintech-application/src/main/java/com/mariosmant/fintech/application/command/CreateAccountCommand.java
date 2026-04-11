package com.mariosmant.fintech.application.command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * CQRS Command: creates a new account.
 *
 * @param ownerName the account owner's full name
 * @param currency  the account currency (ISO 4217)
 * @param initialBalance optional initial deposit amount
 * @author mariosmant
 * @since 1.0.0
 */
public record CreateAccountCommand(
        String ownerName,
        String currency,
        BigDecimal initialBalance
) {

    /**
     * Factory with zero initial balance.
     */
    public static CreateAccountCommand withZeroBalance(String ownerName, String currency) {
        return new CreateAccountCommand(ownerName, currency, BigDecimal.ZERO);
    }
}

