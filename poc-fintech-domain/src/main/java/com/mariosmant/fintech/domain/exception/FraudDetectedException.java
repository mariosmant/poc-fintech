package com.mariosmant.fintech.domain.exception;

/**
 * Thrown when a transaction is rejected by the fraud detection system.
 *
 * @author mariosmant
 * @since 1.0.0
 */
public class FraudDetectedException extends RuntimeException {

    public FraudDetectedException(String message) {
        super(message);
    }
}

