package com.mariosmant.fintech.domain.exception;

/**
 * Thrown when an account has insufficient funds for a debit operation.
 *
 * @author mariosmant
 * @since 1.0.0
 */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String message) {
        super(message);
    }
}

