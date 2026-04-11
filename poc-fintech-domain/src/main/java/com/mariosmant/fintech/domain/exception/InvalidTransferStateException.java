package com.mariosmant.fintech.domain.exception;

/**
 * Thrown when a transfer state transition is illegal
 * (e.g., attempting to debit before fraud check).
 *
 * @author mariosmant
 * @since 1.0.0
 */
public class InvalidTransferStateException extends RuntimeException {

    public InvalidTransferStateException(String message) {
        super(message);
    }
}

