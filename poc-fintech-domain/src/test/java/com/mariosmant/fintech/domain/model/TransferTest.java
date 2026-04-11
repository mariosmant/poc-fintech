package com.mariosmant.fintech.domain.model;

import com.mariosmant.fintech.domain.event.TransferCompletedEvent;
import com.mariosmant.fintech.domain.event.TransferFailedEvent;
import com.mariosmant.fintech.domain.event.TransferInitiatedEvent;
import com.mariosmant.fintech.domain.exception.InvalidTransferStateException;
import com.mariosmant.fintech.domain.model.vo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the {@link Transfer} aggregate.
 *
 * @author mariosmant
 * @since 1.0.0
 */
class TransferTest {

    private Transfer transfer;

    @BeforeEach
    void setUp() {
        transfer = Transfer.initiate(
                AccountId.generate(),
                AccountId.generate(),
                new Money(new BigDecimal("500.00"), Currency.USD),
                Currency.EUR,
                new IdempotencyKey("test-key-001")
        );
    }

    @Test
    @DisplayName("Should start in INITIATED status")
    void shouldStartInitiated() {
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.INITIATED);
    }

    @Test
    @DisplayName("Should register TransferInitiatedEvent on creation")
    void shouldRegisterInitiatedEvent() {
        assertThat(transfer.getDomainEvents()).hasSize(1);
        assertThat(transfer.getDomainEvents().getFirst())
                .isInstanceOf(TransferInitiatedEvent.class);
    }

    @Test
    @DisplayName("Should follow happy-path state transitions")
    void shouldFollowHappyPath() {
        transfer.markFraudChecking();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FRAUD_CHECKING);

        transfer.markFraudChecked(FraudCheckResult.approved(10));
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FX_CONVERTING);

        ExchangeRate rate = new ExchangeRate(Currency.USD, Currency.EUR,
                new BigDecimal("0.925"), Instant.now());
        Money converted = new Money(new BigDecimal("462.50"), Currency.EUR);
        transfer.markFxConverted(rate, converted);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.DEBITING);

        transfer.markDebited();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.CREDITING);

        transfer.markCredited();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.RECORDING_LEDGER);

        transfer.markLedgerRecorded();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(transfer.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("Should register TransferCompletedEvent on completion")
    void shouldRegisterCompletedEvent() {
        // Walk through all states
        transfer.markFraudChecking();
        transfer.markFraudChecked(FraudCheckResult.approved(10));
        transfer.markFxConverted(
                new ExchangeRate(Currency.USD, Currency.EUR, BigDecimal.ONE, Instant.now()),
                transfer.getSourceAmount());
        transfer.markDebited();
        transfer.markCredited();
        transfer.clearEvents();

        transfer.markLedgerRecorded();

        assertThat(transfer.getDomainEvents()).hasSize(1);
        assertThat(transfer.getDomainEvents().getFirst())
                .isInstanceOf(TransferCompletedEvent.class);
    }

    @Test
    @DisplayName("Should throw InvalidTransferStateException on illegal transition")
    void shouldThrowOnIllegalTransition() {
        // Cannot go directly from INITIATED to DEBITING
        assertThatThrownBy(() -> transfer.markDebited())
                .isInstanceOf(InvalidTransferStateException.class);
    }

    @Test
    @DisplayName("Should mark as FAILED and register TransferFailedEvent")
    void shouldMarkFailed() {
        transfer.clearEvents();
        transfer.markFailed("Test failure reason");
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(transfer.getFailureReason()).isEqualTo("Test failure reason");
        assertThat(transfer.isTerminal()).isTrue();
        assertThat(transfer.getDomainEvents().getFirst())
                .isInstanceOf(TransferFailedEvent.class);
    }

    @Test
    @DisplayName("Should detect FX conversion requirement")
    void shouldDetectFxRequirement() {
        assertThat(transfer.requiresFxConversion()).isTrue();

        Transfer sameCurrency = Transfer.initiate(
                AccountId.generate(), AccountId.generate(),
                new Money(new BigDecimal("100"), Currency.USD),
                Currency.USD,
                new IdempotencyKey("same-ccy-key"));
        assertThat(sameCurrency.requiresFxConversion()).isFalse();
    }

    @Test
    @DisplayName("Fraud rejection should mark transfer as FAILED")
    void fraudRejectionShouldFail() {
        transfer.markFraudChecking();
        transfer.markFraudChecked(FraudCheckResult.rejected("Suspicious", 85));
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(transfer.getFailureReason()).contains("Fraud detected");
    }
}

