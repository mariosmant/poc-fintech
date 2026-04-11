package com.mariosmant.fintech.domain.exception;

/**
 * Thrown when a transfer with the same idempotency key already exists.
 * This is expected behaviour — the original response should be returned.
 *
 * @author mariosmant
 * @since 1.0.0
 */
public class DuplicateTransferException extends RuntimeException {

    public DuplicateTransferException(String message) {
        super(message);
    }
}

