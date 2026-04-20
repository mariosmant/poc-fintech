package com.mariosmant.fintech.application.usecase;

import com.mariosmant.fintech.application.command.InitiateTransferCommand;
import com.mariosmant.fintech.application.dto.TransferResponse;
import com.mariosmant.fintech.application.outbox.OutboxEvent;
import com.mariosmant.fintech.application.port.OutboxRepository;
import com.mariosmant.fintech.domain.model.Account;
import com.mariosmant.fintech.domain.model.Transfer;
import com.mariosmant.fintech.domain.model.vo.*;
import com.mariosmant.fintech.domain.port.outbound.AccountRepository;
import com.mariosmant.fintech.domain.port.outbound.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock private AccountRepository accountRepository;

    private InitiateTransferUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new InitiateTransferUseCase(transferRepository, outboxRepository, accountRepository);
    }

    @Test
    @DisplayName("Should create transfer and write outbox event")
    void shouldCreateTransfer() {
        // Given
        UUID targetId = UUID.randomUUID();
        var sourceAccount = Account.createWithBalance("test-user", new Money(new BigDecimal("1000"), Currency.USD));

        when(transferRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(transferRepository.save(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(accountRepository.findById(any())).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByOwnerId("test-user-id")).thenReturn(List.of(sourceAccount));

        var command = new InitiateTransferCommand(
                sourceAccount.getId().value(), targetId, null,
                new BigDecimal("250.00"), Currency.USD, Currency.EUR,
                "idem-key-001", "test-user-id"
        );

        // When
        TransferResponse response = useCase.handle(command);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("INITIATED");
        assertThat(response.sourceAmount()).isEqualByComparingTo("250.00");
        assertThat(response.idempotencyKey()).isEqualTo("idem-key-001");

        verify(transferRepository).save(any(Transfer.class), eq("test-user-id"));
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
                UUID.randomUUID(), UUID.randomUUID(), null,
                new BigDecimal("100.00"), Currency.USD, Currency.USD,
                "dup-key", "test-user-id"
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
        var sourceAccount = Account.createWithBalance("test-user", new Money(new BigDecimal("1000"), Currency.GBP));

        when(transferRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(transferRepository.save(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(accountRepository.findById(any())).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByOwnerId("test-user-id")).thenReturn(List.of(sourceAccount));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        when(outboxRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        var command = new InitiateTransferCommand(
                sourceAccount.getId().value(), UUID.randomUUID(), null,
                new BigDecimal("50"), Currency.GBP, Currency.EUR,
                "outbox-key", "test-user-id"
        );

        useCase.handle(command);

        assertThat(captor.getValue().getAggregateType()).isEqualTo("Transfer");
        assertThat(captor.getValue().getEventType()).isEqualTo("TransferInitiatedEvent");
        assertThat(captor.getValue().getPayload()).contains("\"eventType\":\"TransferInitiatedEvent\"");
        assertThat(captor.getValue().getPayload()).contains("\"transferId\":");
        assertThat(captor.getValue().isPublished()).isFalse();
    }

    @Test
    @DisplayName("Should reject transfer when source account does not belong to user")
    void shouldRejectTransferWhenNotOwner() {
        // Given — source account exists but belongs to a different user
        var sourceAccount = Account.createWithBalance("other-user", new Money(new BigDecimal("1000"), Currency.USD));

        when(transferRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(accountRepository.findById(any())).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByOwnerId("test-user-id")).thenReturn(List.of()); // empty — not owned

        var command = new InitiateTransferCommand(
                sourceAccount.getId().value(), UUID.randomUUID(), null,
                new BigDecimal("100"), Currency.USD, Currency.EUR,
                "key-forbidden", "test-user-id"
        );

        // When / Then
        assertThatThrownBy(() -> useCase.handle(command))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("does not belong to the authenticated user");

        verify(transferRepository, never()).save(any(), any());
    }
}

