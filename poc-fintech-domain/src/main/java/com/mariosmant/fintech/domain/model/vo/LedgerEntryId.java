package com.mariosmant.fintech.domain.model.vo;

import java.util.Objects;
import java.util.UUID;

/**
 * Value Object representing a unique ledger entry identifier.
 *
 * @param value the underlying UUID
 * @author mariosmant
 * @since 1.0.0
 */
public record LedgerEntryId(UUID value) {

    /**
     * Compact constructor enforcing non-null constraint.
     *
     * @param value the UUID; must not be {@code null}
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public LedgerEntryId {
        Objects.requireNonNull(value, "LedgerEntryId value must not be null");
    }

    /** Generates a new random {@code LedgerEntryId}. */
    public static LedgerEntryId generate() {
        return new LedgerEntryId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
