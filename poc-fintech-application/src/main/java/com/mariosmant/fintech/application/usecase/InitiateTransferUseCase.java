package com.mariosmant.fintech.application.usecase;

import com.mariosmant.fintech.application.command.InitiateTransferCommand;
import com.mariosmant.fintech.application.dto.TransferResponse;
import com.mariosmant.fintech.application.outbox.OutboxEvent;
import com.mariosmant.fintech.application.port.OutboxRepository;
import com.mariosmant.fintech.application.serialization.EventPayloadSerializer;
import com.mariosmant.fintech.domain.event.DomainEvent;
import com.mariosmant.fintech.domain.exception.DuplicateTransferException;
import com.mariosmant.fintech.domain.model.Account;
import com.mariosmant.fintech.domain.model.Transfer;
import com.mariosmant.fintech.domain.model.vo.AccountId;
import com.mariosmant.fintech.domain.model.vo.IdempotencyKey;
import com.mariosmant.fintech.domain.model.vo.Money;
import com.mariosmant.fintech.domain.port.outbound.AccountRepository;
import com.mariosmant.fintech.domain.port.outbound.TransferRepository;
import com.mariosmant.fintech.domain.util.IbanUtil;
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
    private final AccountRepository accountRepository;

    public InitiateTransferUseCase(TransferRepository transferRepository,
                                   OutboxRepository outboxRepository,
                                   AccountRepository accountRepository) {
        this.transferRepository = transferRepository;
        this.outboxRepository = outboxRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Handles the transfer initiation command.
     *
     * @param command the initiation command
     * @return the transfer response DTO
     * @throws DuplicateTransferException if idempotency key already exists
     * @throws SecurityException if the source account is not owned by the initiating user
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

        // Ownership check — source account must belong to the authenticated user (IDOR prevention)
        var sourceAccountId = new AccountId(command.sourceAccountId());
        var sourceAccount = accountRepository.findById(sourceAccountId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Source account not found: " + command.sourceAccountId()));
        // Verify via persistence layer that ownerId matches the JWT user
        accountRepository.findByOwnerId(command.initiatedBy()).stream()
                .filter(a -> a.getId().equals(sourceAccountId))
                .findFirst()
                .orElseThrow(() -> new SecurityException(
                        "Source account does not belong to the authenticated user"));

        // Resolve target — either the supplied UUID or an IBAN (both accepted; UUID wins when present)
        Account targetAccount = resolveTargetAccount(command);

        // Create the aggregate
        var sourceAmount = new Money(command.amount(), command.sourceCurrency());
        var transfer = Transfer.initiate(
                sourceAccountId,
                targetAccount.getId(),
                sourceAmount,
                command.targetCurrency(),
                idempotencyKey
        );

        // Persist aggregate + outbox events in the same transaction boundary
        Transfer saved = transferRepository.save(transfer, command.initiatedBy());

        // Use original aggregate events (the remapped saved instance has no in-memory events).
        for (DomainEvent event : transfer.getDomainEvents()) {
            var outboxEvent = OutboxEvent.create(
                    "Transfer",
                    saved.getId().toString(),
                    event.getClass().getSimpleName(),
                    serializeEvent(event)
            );
            outboxRepository.save(outboxEvent);
        }
        transfer.clearEvents();

        log.info("Transfer initiated: id={}, source={}, target={}, amount={}, initiatedBy={}",
                saved.getId(), command.sourceAccountId(),
                targetAccount.getId().value(), sourceAmount, command.initiatedBy());

        return toResponse(saved, sourceAccount.getIban(), targetAccount.getIban());
    }

    private Account resolveTargetAccount(InitiateTransferCommand command) {
        if (command.targetAccountId() != null) {
            return accountRepository.findById(new AccountId(command.targetAccountId()))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Target account not found: " + command.targetAccountId()));
        }
        if (command.targetIban() != null && !command.targetIban().isBlank()) {
            String normalized = IbanUtil.normalize(command.targetIban());
            if (!IbanUtil.isValid(normalized)) {
                throw new IllegalArgumentException("Invalid target IBAN: " + command.targetIban());
            }
            return accountRepository.findByIban(normalized)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Target IBAN not recognised by this institution: " + normalized));
        }
        throw new IllegalArgumentException("Either targetAccountId or targetIban must be provided");
    }

    private TransferResponse toResponse(Transfer t) {
        // Called only on idempotency hit — look up IBANs for a consistent response shape.
        String sourceIban = accountRepository.findById(t.getSourceAccountId())
                .map(Account::getIban).orElse(null);
        String targetIban = accountRepository.findById(t.getTargetAccountId())
                .map(Account::getIban).orElse(null);
        return toResponse(t, sourceIban, targetIban);
    }

    private TransferResponse toResponse(Transfer t, String sourceIban, String targetIban) {
        return new TransferResponse(
                t.getId().value(),
                t.getStatus().name(),
                t.getSourceAccountId().value(),
                sourceIban,
                t.getTargetAccountId().value(),
                targetIban,
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
     * Serializes a domain event as JSON for outbox/Kafka transport.
     */
    private String serializeEvent(DomainEvent event) {
        return EventPayloadSerializer.toJson(event);
    }
}

