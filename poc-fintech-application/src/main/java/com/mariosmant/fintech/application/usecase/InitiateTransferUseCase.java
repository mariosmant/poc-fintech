package com.mariosmant.fintech.application.usecase;

import com.mariosmant.fintech.application.command.InitiateTransferCommand;
import com.mariosmant.fintech.application.dto.TransferResponse;
import com.mariosmant.fintech.application.outbox.OutboxEvent;
import com.mariosmant.fintech.application.port.OutboxRepository;
import com.mariosmant.fintech.domain.event.DomainEvent;
import com.mariosmant.fintech.domain.exception.DuplicateTransferException;
import com.mariosmant.fintech.domain.model.Transfer;
import com.mariosmant.fintech.domain.model.vo.*;
import com.mariosmant.fintech.domain.port.outbound.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Use Case: Initiate a money transfer (CQRS write side).
 *
 * <p>This is the primary entry point for the write side. It:
 * <ol>
 *   <li>Checks idempotency — returns existing response on duplicate key.</li>
 *   <li>Creates the Transfer aggregate in INITIATED state.</li>
 *   <li>Persists the aggregate.</li>
 *   <li>Writes the domain event to the Transactional Outbox.</li>
 * </ol></p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public class InitiateTransferUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateTransferUseCase.class);

    private final TransferRepository transferRepository;
    private final OutboxRepository outboxRepository;

    public InitiateTransferUseCase(TransferRepository transferRepository,
                                   OutboxRepository outboxRepository) {
        this.transferRepository = transferRepository;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Handles the transfer initiation command.
     *
     * @param command the initiation command
     * @return the transfer response DTO
     * @throws DuplicateTransferException if idempotency key already exists
     */
    public TransferResponse handle(InitiateTransferCommand command) {
        var idempotencyKey = new IdempotencyKey(command.idempotencyKey());

        // Idempotency check — return existing transfer if key already used
        var existing = transferRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Duplicate idempotency key detected, returning existing transfer: {}",
                    existing.get().getId());
            return toResponse(existing.get());
        }

        // Create the aggregate
        var sourceAmount = new Money(command.amount(), command.sourceCurrency());
        var transfer = Transfer.initiate(
                new AccountId(command.sourceAccountId()),
                new AccountId(command.targetAccountId()),
                sourceAmount,
                command.targetCurrency(),
                idempotencyKey
        );

        // Persist aggregate + outbox event in the same transaction
        Transfer saved = transferRepository.save(transfer);

        // Write domain events to the outbox
        for (DomainEvent event : saved.getDomainEvents()) {
            var outboxEvent = OutboxEvent.create(
                    "Transfer",
                    saved.getId().toString(),
                    event.getClass().getSimpleName(),
                    serializeEvent(event)
            );
            outboxRepository.save(outboxEvent);
        }
        saved.clearEvents();

        log.info("Transfer initiated: id={}, source={}, target={}, amount={}",
                saved.getId(), command.sourceAccountId(),
                command.targetAccountId(), sourceAmount);

        return toResponse(saved);
    }

    private TransferResponse toResponse(Transfer t) {
        return new TransferResponse(
                t.getId().value(),
                t.getStatus().name(),
                t.getSourceAccountId().value(),
                t.getTargetAccountId().value(),
                t.getSourceAmount().amount(),
                t.getSourceAmount().currency().name(),
                t.getTargetAmount() != null ? t.getTargetAmount().amount() : null,
                t.getTargetCurrency().name(),
                t.getExchangeRate() != null ? t.getExchangeRate().rate() : null,
                t.getFailureReason(),
                t.getIdempotencyKey().value()
        );
    }

    /**
     * Serializes a domain event to JSON string for the outbox payload.
     * The infrastructure layer will provide the actual Jackson ObjectMapper.
     * For now, we use a simple toString representation.
     */
    private String serializeEvent(DomainEvent event) {
        // The infrastructure layer will override this via an EventSerializer component
        return event.toString();
    }
}

