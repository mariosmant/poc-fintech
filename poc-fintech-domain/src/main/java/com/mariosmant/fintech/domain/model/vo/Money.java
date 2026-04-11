package com.mariosmant.fintech.domain.model.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value Object representing a monetary amount in a specific currency.
 *
 * <p>Immutable. All arithmetic returns new instances. Uses
 * {@link RoundingMode#HALF_EVEN} (banker's rounding) per financial standards.
 * Scale is determined by the currency's ISO 4217 decimal places.</p>
 *
 * <p><b>Design decision:</b> {@code BigDecimal} over {@code double} to avoid
 * floating-point precision errors — critical in fintech applications.</p>
 *
 * @param amount   the monetary amount (scaled to the currency's decimals)
 * @param currency the ISO 4217 currency
 * @author mariosmant
 * @since 1.0.0
 */
public record Money(BigDecimal amount, Currency currency) {

    /** The rounding mode used across all monetary calculations. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    /**
     * Compact constructor: normalises scale and enforces non-null constraints.
     */
    public Money {
        Objects.requireNonNull(amount, "Money amount must not be null");
        Objects.requireNonNull(currency, "Money currency must not be null");
        amount = amount.setScale(currency.getDecimalPlaces(), ROUNDING);
    }

    /**
     * Convenience factory for creating {@code Money} from a {@code long} minor units value.
     *
     * @param minorUnits amount in minor units (e.g., cents)
     * @param currency   the currency
     * @return a new {@code Money}
     */
    public static Money ofMinor(long minorUnits, Currency currency) {
        BigDecimal amt = BigDecimal.valueOf(minorUnits)
                .movePointLeft(currency.getDecimalPlaces());
        return new Money(amt, currency);
    }

    /**
     * Creates a {@code Money} of zero in the given currency.
     *
     * @param currency the currency
     * @return zero-amount Money
     */
    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    /**
     * Adds another {@code Money} to this one.
     *
     * @param other the amount to add
     * @return a new {@code Money} with the sum
     * @throws IllegalArgumentException if currencies differ
     */
    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    /**
     * Subtracts another {@code Money} from this one.
     *
     * @param other the amount to subtract
     * @return a new {@code Money} with the difference
     * @throws IllegalArgumentException if currencies differ
     */
    public Money subtract(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    /**
     * Returns the negated value of this money.
     *
     * @return a new {@code Money} with negated amount
     */
    public Money negate() {
        return new Money(this.amount.negate(), this.currency);
    }

    /** @return {@code true} if the amount is strictly positive */
    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /** @return {@code true} if the amount is zero */
    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    /** @return {@code true} if the amount is negative */
    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * Checks that both operands share the same currency.
     *
     * @param other the other Money operand
     */
    private void assertSameCurrency(Money other) {
        if (this.currency != other.currency) {
            throw new IllegalArgumentException(
                    "Currency mismatch: %s vs %s".formatted(this.currency, other.currency));
        }
    }

    @Override
    public String toString() {
        return "%s %s".formatted(amount.toPlainString(), currency);
    }
}

