package com.mariosmant.fintech.domain.model.vo;

import java.util.Objects;

/**
 * Value Object representing a client-supplied idempotency key.
 *
 * <p>Used to ensure <b>exactly-once</b> processing of transfer requests.
 * The key is hashed using SHA3-256 (NIST-approved,
 * collision-minimized) before storage to prevent enumeration attacks.</p>
 *
 * @param value the raw idempotency key string
 * @author mariosmant
 * @since 1.0.0
 */
public record IdempotencyKey(String value) {

    /**
     * Compact constructor enforcing non-null, non-blank constraint.
     *
     * @throws IllegalArgumentException if the value is blank
     */
    public IdempotencyKey {
        Objects.requireNonNull(value, "IdempotencyKey must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("IdempotencyKey must not be blank");
        }
    }

    @Override
    public String toString() {
        // Mask the key in logs for security (NIST SP 800-92 log sanitization)
        return "IdempotencyKey[***]";
    }
}

