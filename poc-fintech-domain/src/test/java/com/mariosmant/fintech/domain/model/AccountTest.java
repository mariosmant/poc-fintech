package com.mariosmant.fintech.domain.model;

import com.mariosmant.fintech.domain.event.AccountCreditedEvent;
import com.mariosmant.fintech.domain.event.AccountDebitedEvent;
import com.mariosmant.fintech.domain.exception.InsufficientFundsException;
import com.mariosmant.fintech.domain.model.vo.Currency;
import com.mariosmant.fintech.domain.model.vo.Money;
import com.mariosmant.fintech.domain.model.vo.TransferId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the {@link Account} aggregate.
 *
 * @author mariosmant
 * @since 1.0.0
 */
class AccountTest {

    private Account account;
    private TransferId transferId;

    @BeforeEach
    void setUp() {
        account = Account.createWithBalance("Alice",
                new Money(new BigDecimal("1000.00"), Currency.USD));
        transferId = TransferId.generate();
    }

    @Test
    @DisplayName("Should debit account and reduce balance")
    void shouldDebit() {
        account.debit(new Money(new BigDecimal("300.00"), Currency.USD), transferId);
        assertThat(account.getBalance().amount()).isEqualByComparingTo("700.00");
    }

    @Test
    @DisplayName("Should register AccountDebitedEvent on debit")
    void shouldRegisterDebitEvent() {
        account.debit(new Money(new BigDecimal("100.00"), Currency.USD), transferId);
        assertThat(account.getDomainEvents()).hasSize(1);
        assertThat(account.getDomainEvents().getFirst()).isInstanceOf(AccountDebitedEvent.class);
    }

    @Test
    @DisplayName("Should credit account and increase balance")
    void shouldCredit() {
        account.credit(new Money(new BigDecimal("500.00"), Currency.USD), transferId);
        assertThat(account.getBalance().amount()).isEqualByComparingTo("1500.00");
    }

    @Test
    @DisplayName("Should register AccountCreditedEvent on credit")
    void shouldRegisterCreditEvent() {
        account.credit(new Money(new BigDecimal("200.00"), Currency.USD), transferId);
        assertThat(account.getDomainEvents()).hasSize(1);
        assertThat(account.getDomainEvents().getFirst()).isInstanceOf(AccountCreditedEvent.class);
    }

    @Test
    @DisplayName("Should throw InsufficientFundsException when balance would go negative")
    void shouldThrowOnInsufficientFunds() {
        assertThatThrownBy(() ->
                account.debit(new Money(new BigDecimal("1500.00"), Currency.USD), transferId))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("insufficient funds");
    }

    @Test
    @DisplayName("Should throw on non-positive debit amount")
    void shouldThrowOnZeroDebit() {
        assertThatThrownBy(() ->
                account.debit(new Money(BigDecimal.ZERO, Currency.USD), transferId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    @DisplayName("Should throw on currency mismatch")
    void shouldThrowOnCurrencyMismatch() {
        assertThatThrownBy(() ->
                account.debit(new Money(new BigDecimal("100"), Currency.EUR), transferId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");
    }

    @Test
    @DisplayName("Should clear events after clearEvents()")
    void shouldClearEvents() {
        account.debit(new Money(new BigDecimal("100.00"), Currency.USD), transferId);
        assertThat(account.getDomainEvents()).hasSize(1);
        account.clearEvents();
        assertThat(account.getDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("Factory create() should produce zero-balance account")
    void factoryCreateShouldProduceZeroBalance() {
        Account acc = Account.create("Bob", Currency.EUR);
        assertThat(acc.getBalance().isZero()).isTrue();
        assertThat(acc.getBalance().currency()).isEqualTo(Currency.EUR);
    }
}

