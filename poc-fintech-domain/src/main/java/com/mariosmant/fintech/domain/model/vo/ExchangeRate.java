package com.mariosmant.fintech.domain.model.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Value Object representing an FX exchange rate between two currencies.
 *
 * <p>Captures the rate at a specific point in time. Rate is always expressed
 * as: 1 unit of {@code source} = {@code rate} units of {@code target}.</p>
 *
 * @param source    the source (base) currency
 * @param target    the target (quote) currency
 * @param rate      the conversion rate; must be positive
 * @param timestamp when this rate was observed
 * @author mariosmant
 * @since 1.0.0
 */
public record ExchangeRate(Currency source, Currency target, BigDecimal rate, Instant timestamp) {

    public ExchangeRate {
        Objects.requireNonNull(source, "Source currency must not be null");
        Objects.requireNonNull(target, "Target currency must not be null");
        Objects.requireNonNull(rate, "Rate must not be null");
        Objects.requireNonNull(timestamp, "Timestamp must not be null");
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Exchange rate must be positive, got: " + rate);
        }
    }

    /**
     * Converts a {@link Money} from the source currency to the target currency
     * using this exchange rate.
     *
     * @param money the money to convert; must be in the source currency
     * @return a new {@code Money} in the target currency
     * @throws IllegalArgumentException if the money currency does not match the source
     */
    public Money convert(Money money) {
        if (money.currency() != source) {
            throw new IllegalArgumentException(
                    "Cannot convert %s using rate for %s→%s".formatted(
                            money.currency(), source, target));
        }
        BigDecimal converted = money.amount().multiply(rate);
        return new Money(converted, target);
    }
}

