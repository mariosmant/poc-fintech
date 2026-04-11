package com.mariosmant.fintech.domain.model.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ExchangeRate} value object.
 *
 * @author mariosmant
 * @since 1.0.0
 */
class ExchangeRateTest {

    @Test
    @DisplayName("Should convert money using the exchange rate")
    void shouldConvertMoney() {
        ExchangeRate rate = new ExchangeRate(
                Currency.USD, Currency.EUR, new BigDecimal("0.925"), Instant.now());
        Money usd = new Money(new BigDecimal("100.00"), Currency.USD);
        Money eur = rate.convert(usd);

        assertThat(eur.currency()).isEqualTo(Currency.EUR);
        assertThat(eur.amount()).isEqualByComparingTo("92.50");
    }

    @Test
    @DisplayName("Should reject non-positive rate")
    void shouldRejectNonPositiveRate() {
        assertThatThrownBy(() -> new ExchangeRate(
                Currency.USD, Currency.EUR, BigDecimal.ZERO, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    @DisplayName("Should reject converting wrong currency")
    void shouldRejectWrongCurrency() {
        ExchangeRate rate = new ExchangeRate(
                Currency.USD, Currency.EUR, new BigDecimal("0.925"), Instant.now());
        Money gbp = new Money(new BigDecimal("100"), Currency.GBP);

        assertThatThrownBy(() -> rate.convert(gbp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot convert");
    }

    @Test
    @DisplayName("Identity rate should return same amount")
    void identityRate() {
        ExchangeRate rate = new ExchangeRate(
                Currency.USD, Currency.USD, BigDecimal.ONE, Instant.now());
        Money m = new Money(new BigDecimal("250.00"), Currency.USD);
        assertThat(rate.convert(m).amount()).isEqualByComparingTo("250.00");
    }
}

