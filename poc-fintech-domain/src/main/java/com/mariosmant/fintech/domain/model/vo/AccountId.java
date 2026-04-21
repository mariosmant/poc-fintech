package com.mariosmant.fintech.domain.model.vo;

import java.util.Objects;
import java.util.UUID;

/**
 * Value Object representing a unique account identifier.
 *
 * <p>Immutable, identity-based VO following DDD principles.
 * Uses {@link java.util.UUID} to guarantee global uniqueness with
 * cryptographic collision resistance (128-bit entropy).</p>
 *
 * @param value the underlying UUID
 * @author mariosmant
 * @since 1.0.0
 */
public record AccountId(UUID value) implements HasId<UUID> {

    /**
     * Constructs an {@code AccountId} ensuring the value is non-null.
     *
     * @param value the UUID; must not be {@code null}
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public AccountId {
        Objects.requireNonNull(value, "AccountId value must not be null");
    }

    /**
     * Factory method: generates a new random {@code AccountId}.
     *
     * @return a newly generated {@code AccountId}
     */
    public static AccountId generate() {
        return new AccountId(UUID.randomUUID());
    }

    /**
     * Factory method: reconstitutes an {@code AccountId} from a UUID string.
     *
     * @param raw the UUID string
     * @return the parsed {@code AccountId}
     * @throws IllegalArgumentException if the string is not a valid UUID
     */
    public static AccountId from(String raw) {
        return new AccountId(UUID.fromString(raw));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
