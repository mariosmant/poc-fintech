package com.mariosmant.fintech.domain.model.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * {@code Money} — <b>Value Object</b> (DDD) representing a monetary amount
 * in a specific ISO 4217 currency.
 *
 * <h2>Architecture role</h2>
 * <ul>
 *   <li><b>DDD Value Object (Evans, ch. 5):</b> identity-less, immutable,
 *       structurally compared. Implemented as a Java {@code record} so
 *       equality, hashing, and debug-string are contract-derived from its
 *       two components ({@code amount}, {@code currency}).</li>
 *   <li><b>Ubiquitous language:</b> every monetary quantity in the domain
 *       — account balance, transfer amount, ledger debit/credit — is a
 *       {@code Money}. {@code BigDecimal} never appears raw in a domain
 *       signature; you cannot accidentally pass an EUR amount to a USD
 *       slot because the types differ.</li>
 * </ul>
 *
 * <h2>Why {@link BigDecimal}, why banker's rounding</h2>
 * <ul>
 *   <li><b>No IEEE-754 / double:</b> floating-point binary cannot represent
 *       {@code 0.1} exactly, so {@code 0.1 + 0.2 != 0.3}. A single
 *       accumulated rounding error on a reconciliation run can fail external
 *       audits (ISO 4217 + IFRS require exact decimal arithmetic — FR 2017-02,
 *       EU MiFID II RTS 22). {@code BigDecimal} guarantees bit-exact decimals.</li>
 *   <li><b>Scale follows the currency:</b> {@code JPY}/{@code KRW} use scale 0
 *       (no minor units), most currencies use scale 2, a handful use scale 3
 *       (BHD, KWD, OMR). The compact constructor enforces that on every
 *       instantiation so {@code Money(10.5, JPY)} becomes {@code 11 JPY},
 *       never {@code 10.50 JPY}.</li>
 *   <li><b>{@link RoundingMode#HALF_EVEN} (banker's rounding)</b> is the
 *       financial-industry default — unlike {@code HALF_UP} it is statistically
 *       unbiased over large populations of transactions, which matters for
 *       fee accruals and FX spreads (IEEE 754 §4.3.3 calls the same rule
 *       "roundTiesToEven"; it's also the default in Excel/SAP/Oracle FS).</li>
 * </ul>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>Amount and currency are both non-null.</li>
 *   <li>Arithmetic ({@link #add} / {@link #subtract}) requires operands of
 *       the <i>same</i> currency — mismatches throw at runtime rather than
 *       silently producing a meaningless sum.</li>
 *   <li>Immutable — every operation returns a new instance; thread-safe by
 *       construction.</li>
 * </ul>
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

