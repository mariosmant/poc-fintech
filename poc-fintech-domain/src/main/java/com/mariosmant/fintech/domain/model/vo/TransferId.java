package com.mariosmant.fintech.domain.model.vo;

import java.util.Objects;
import java.util.UUID;

/**
 * Value Object representing a unique transfer (money movement) identifier.
 *
 * @param value the underlying UUID
 * @author mariosmant
 * @since 1.0.0
 */
public record TransferId(UUID value) {

    public TransferId {
        Objects.requireNonNull(value, "TransferId value must not be null");
    }

    /** Generates a new random {@code TransferId}. */
    public static TransferId generate() {
        return new TransferId(UUID.randomUUID());
    }

    public static TransferId from(String raw) {
        return new TransferId(UUID.fromString(raw));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

