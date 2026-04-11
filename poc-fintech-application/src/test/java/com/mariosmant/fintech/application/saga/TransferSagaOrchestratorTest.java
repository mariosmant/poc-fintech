package com.mariosmant.fintech.application.saga;

import com.mariosmant.fintech.application.outbox.OutboxEvent;
import com.mariosmant.fintech.application.port.OutboxRepository;
import com.mariosmant.fintech.domain.event.TransferInitiatedEvent;
import com.mariosmant.fintech.domain.model.Account;
import com.mariosmant.fintech.domain.model.Transfer;
import com.mariosmant.fintech.domain.model.vo.*;
import com.mariosmant.fintech.domain.port.outbound.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link TransferSagaOrchestrator}.
 *
 * @author mariosmant
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class TransferSagaOrchestratorTest {

    @Mock private TransferRepository transferRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private LedgerRepository ledgerRepository;
    @Mock private OutboxRepository outboxRepository;
    @Mock private FraudDetectionPort fraudDetectionPort;
    @Mock private FxRatePort fxRatePort;

    private TransferSagaOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new TransferSagaOrchestrator(
                transferRepository, accountRepository, ledgerRepository,
                outboxRepository, fraudDetectionPort, fxRatePort);
    }

    @Test
    @DisplayName("Should process TransferInitiatedEvent — fraud check approved")
    void shouldProcessInitiatedEvent() {
        // Given
        Transfer transfer = Transfer.initiate(
                AccountId.generate(), AccountId.generate(),
                new Money(new BigDecimal("500"), Currency.USD),
                Currency.EUR, new IdempotencyKey("key-1"));
        transfer.clearEvents();

        when(transferRepository.findById(any())).thenReturn(Optional.of(transfer));
        when(fraudDetectionPort.check(any())).thenReturn(FraudCheckResult.approved(10));
        when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        var event = new TransferInitiatedEvent(
                UUID.randomUUID(), transfer.getId().toString(), Instant.now(),
                transfer.getId(), transfer.getSourceAccountId(), transfer.getTargetAccountId(),
                new BigDecimal("500"), Currency.USD, Currency.EUR, "key-1");

        orchestrator.handle(event);

        // Then
        verify(fraudDetectionPort).check(any());
        verify(transferRepository, atLeastOnce()).save(any());
    }

    @Test
    @DisplayName("Should fail transfer when fraud is detected")
    void shouldFailOnFraud() {
        Transfer transfer = Transfer.initiate(
                AccountId.generate(), AccountId.generate(),
                new Money(new BigDecimal("15000"), Currency.USD),
                Currency.USD, new IdempotencyKey("fraud-key"));
        transfer.clearEvents();

        when(transferRepository.findById(any())).thenReturn(Optional.of(transfer));
        when(fraudDetectionPort.check(any()))
                .thenReturn(FraudCheckResult.rejected("High value", 85));
        when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var event = new TransferInitiatedEvent(
                UUID.randomUUID(), transfer.getId().toString(), Instant.now(),
                transfer.getId(), transfer.getSourceAccountId(), transfer.getTargetAccountId(),
                new BigDecimal("15000"), Currency.USD, Currency.USD, "fraud-key");

        orchestrator.handle(event);

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(transfer.getFailureReason()).contains("Fraud detected");
    }
}

