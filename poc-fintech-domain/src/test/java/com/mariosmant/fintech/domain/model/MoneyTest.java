package com.mariosmant.fintech.domain.model;

import com.mariosmant.fintech.domain.model.vo.Currency;
import com.mariosmant.fintech.domain.model.vo.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the {@link Money} value object.
 *
 * @author mariosmant
 * @since 1.0.0
 */
class MoneyTest {

    @Test
    @DisplayName("Should scale to currency's decimal places on construction")
    void shouldScaleOnConstruction() {
        Money m = new Money(new BigDecimal("100.999"), Currency.USD);
        assertThat(m.amount()).isEqualByComparingTo("101.00");
    }

    @Test
    @DisplayName("JPY should have 0 decimal places")
    void jpyShouldHaveZeroDecimals() {
        Money m = new Money(new BigDecimal("1500.7"), Currency.JPY);
        assertThat(m.amount()).isEqualByComparingTo("1501");
    }

    @Test
    @DisplayName("Should add two Money values of same currency")
    void shouldAdd() {
        Money a = new Money(new BigDecimal("100.00"), Currency.EUR);
        Money b = new Money(new BigDecimal("50.50"), Currency.EUR);
        assertThat(a.add(b).amount()).isEqualByComparingTo("150.50");
    }

    @Test
    @DisplayName("Should subtract two Money values of same currency")
    void shouldSubtract() {
        Money a = new Money(new BigDecimal("100.00"), Currency.EUR);
        Money b = new Money(new BigDecimal("30.25"), Currency.EUR);
        assertThat(a.subtract(b).amount()).isEqualByComparingTo("69.75");
    }

    @Test
    @DisplayName("Should throw on currency mismatch")
    void shouldThrowOnCurrencyMismatch() {
        Money usd = new Money(new BigDecimal("100"), Currency.USD);
        Money eur = new Money(new BigDecimal("50"), Currency.EUR);
        assertThatThrownBy(() -> usd.add(eur))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");
    }

    @Test
    @DisplayName("Should negate correctly")
    void shouldNegate() {
        Money m = new Money(new BigDecimal("42.00"), Currency.GBP);
        assertThat(m.negate().amount()).isEqualByComparingTo("-42.00");
    }

    @Nested
    @DisplayName("Predicates")
    class Predicates {

        @Test
        void isPositive() {
            assertThat(new Money(new BigDecimal("1"), Currency.USD).isPositive()).isTrue();
            assertThat(Money.zero(Currency.USD).isPositive()).isFalse();
        }

        @Test
        void isZero() {
            assertThat(Money.zero(Currency.EUR).isZero()).isTrue();
        }

        @Test
        void isNegative() {
            assertThat(new Money(new BigDecimal("-5"), Currency.CHF).isNegative()).isTrue();
        }
    }

    @Test
    @DisplayName("ofMinor should convert cents to dollars")
    void ofMinorShouldConvert() {
        Money m = Money.ofMinor(1050, Currency.USD);
        assertThat(m.amount()).isEqualByComparingTo("10.50");
    }

    @Test
    @DisplayName("Should reject null amount")
    void shouldRejectNullAmount() {
        assertThatThrownBy(() -> new Money(null, Currency.USD))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should reject null currency")
    void shouldRejectNullCurrency() {
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, null))
                .isInstanceOf(NullPointerException.class);
    }
}

