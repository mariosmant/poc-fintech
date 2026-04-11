package com.mariosmant.fintech.application.usecase;

import com.mariosmant.fintech.application.command.InitiateTransferCommand;
import com.mariosmant.fintech.application.dto.TransferResponse;
import com.mariosmant.fintech.application.outbox.OutboxEvent;
import com.mariosmant.fintech.application.port.OutboxRepository;
import com.mariosmant.fintech.domain.model.Transfer;
import com.mariosmant.fintech.domain.model.vo.*;
import com.mariosmant.fintech.domain.port.outbound.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link InitiateTransferUseCase}.
 *
 * @author mariosmant
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class InitiateTransferUseCaseTest {

    @Mock private TransferRepository transferRepository;
    @Mock private OutboxRepository outboxRepository;

    private InitiateTransferUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new InitiateTransferUseCase(transferRepository, outboxRepository);
    }

    @Test
    @DisplayName("Should create transfer and write outbox event")
    void shouldCreateTransfer() {
        // Given
        when(transferRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var command = new InitiateTransferCommand(
                UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("250.00"), Currency.USD, Currency.EUR,
                "idem-key-001"
        );

        // When
        TransferResponse response = useCase.handle(command);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("INITIATED");
        assertThat(response.sourceAmount()).isEqualByComparingTo("250.00");
        assertThat(response.idempotencyKey()).isEqualTo("idem-key-001");

        verify(transferRepository).save(any(Transfer.class));
        verify(outboxRepository, atLeastOnce()).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("Should return existing transfer on duplicate idempotency key")
    void shouldReturnExistingOnDuplicate() {
        // Given
        Transfer existing = Transfer.initiate(
                AccountId.generate(), AccountId.generate(),
                new Money(new BigDecimal("100"), Currency.USD),
                Currency.USD, new IdempotencyKey("dup-key"));
        existing.clearEvents();

        when(transferRepository.findByIdempotencyKey(any()))
                .thenReturn(Optional.of(existing));

        var command = new InitiateTransferCommand(
                UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100.00"), Currency.USD, Currency.USD,
                "dup-key"
        );

        // When
        TransferResponse response = useCase.handle(command);

        // Then
        assertThat(response.id()).isEqualTo(existing.getId().value());
        // Should NOT create a new transfer
        verify(transferRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should write outbox event with correct aggregate type")
    void shouldWriteOutboxWithCorrectType() {
        when(transferRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        when(outboxRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        var command = new InitiateTransferCommand(
                UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("50"), Currency.GBP, Currency.EUR,
                "outbox-key"
        );

        useCase.handle(command);

        assertThat(captor.getValue().getAggregateType()).isEqualTo("Transfer");
        assertThat(captor.getValue().getEventType()).isEqualTo("TransferInitiatedEvent");
        assertThat(captor.getValue().isPublished()).isFalse();
    }
}

