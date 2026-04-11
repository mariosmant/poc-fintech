package com.mariosmant.fintech.domain.model;

import com.mariosmant.fintech.domain.model.vo.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the {@link LedgerEntry} entity.
 *
 * @author mariosmant
 * @since 1.0.0
 */
class LedgerEntryTest {

    @Test
    @DisplayName("Should create a valid ledger entry")
    void shouldCreateValidEntry() {
        LedgerEntry entry = LedgerEntry.create(
                AccountId.generate(),
                AccountId.generate(),
                new Money(new BigDecimal("500.00"), Currency.USD),
                TransferId.generate()
        );

        assertThat(entry.getId()).isNotNull();
        assertThat(entry.getAmount().isPositive()).isTrue();
        assertThat(entry.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should reject non-positive amount (double-entry invariant)")
    void shouldRejectNonPositiveAmount() {
        assertThatThrownBy(() -> LedgerEntry.create(
                AccountId.generate(),
                AccountId.generate(),
                Money.zero(Currency.USD),
                TransferId.generate()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    @DisplayName("Should reject negative amount")
    void shouldRejectNegativeAmount() {
        assertThatThrownBy(() -> LedgerEntry.create(
                AccountId.generate(),
                AccountId.generate(),
                new Money(new BigDecimal("-100"), Currency.EUR),
                TransferId.generate()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

