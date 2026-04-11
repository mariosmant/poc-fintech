package com.mariosmant.fintech.application.saga;

import com.mariosmant.fintech.application.outbox.OutboxEvent;
import com.mariosmant.fintech.application.port.OutboxRepository;
import com.mariosmant.fintech.domain.event.*;
import com.mariosmant.fintech.domain.model.Account;
import com.mariosmant.fintech.domain.model.LedgerEntry;
import com.mariosmant.fintech.domain.model.Transfer;
import com.mariosmant.fintech.domain.model.vo.*;
import com.mariosmant.fintech.domain.port.outbound.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

/**
 * Saga Orchestrator for the money-transfer flow.
 *
 * <p>Orchestrates the following steps:
 * <ol>
 *   <li>{@code TransferInitiatedEvent} → Fraud check</li>
 *   <li>{@code FraudCheckCompletedEvent} → FX conversion (if cross-currency)</li>
 *   <li>{@code FxConversionCompletedEvent} → Debit source account</li>
 *   <li>{@code AccountDebitedEvent} → Credit target account</li>
 *   <li>{@code AccountCreditedEvent} → Record double-entry ledger</li>
 *   <li>{@code LedgerEntryRecordedEvent} → Mark transfer completed</li>
 * </ol>
 *
 * <p>On any failure, the orchestrator triggers compensation (reversal)
 * of already-executed steps and marks the transfer as FAILED.</p>
 *
 * <p><b>Exactly-once semantics:</b> Each step checks the transfer's current
 * status before proceeding, making the handler idempotent.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public class TransferSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(TransferSagaOrchestrator.class);

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final LedgerRepository ledgerRepository;
    private final OutboxRepository outboxRepository;
    private final FraudDetectionPort fraudDetectionPort;
    private final FxRatePort fxRatePort;

    public TransferSagaOrchestrator(TransferRepository transferRepository,
                                    AccountRepository accountRepository,
                                    LedgerRepository ledgerRepository,
                                    OutboxRepository outboxRepository,
                                    FraudDetectionPort fraudDetectionPort,
                                    FxRatePort fxRatePort) {
        this.transferRepository = transferRepository;
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.outboxRepository = outboxRepository;
        this.fraudDetectionPort = fraudDetectionPort;
        this.fxRatePort = fxRatePort;
    }

    /**
     * Dispatches a domain event to the appropriate saga step handler.
     *
     * @param event the domain event to process
     */
    public void handle(DomainEvent event) {
        log.debug("Saga handling event: type={}, aggregateId={}",
                event.getClass().getSimpleName(), event.aggregateId());

        switch (event) {
            case TransferInitiatedEvent e -> handleTransferInitiated(e);
            case FraudCheckCompletedEvent e -> handleFraudCheckCompleted(e);
            case FxConversionCompletedEvent e -> handleFxConversionCompleted(e);
            case AccountDebitedEvent e -> handleAccountDebited(e);
            case AccountCreditedEvent e -> handleAccountCredited(e);
            case LedgerEntryRecordedEvent e -> handleLedgerRecorded(e);
            case TransferCompletedEvent e ->
                    log.info("Transfer completed: {}", e.transferId());
            case TransferFailedEvent e ->
                    log.warn("Transfer failed: {} reason={}", e.transferId(), e.reason());
        }
    }

    // ── Step 1: Fraud Check ──────────────────────────────────────────────

    private void handleTransferInitiated(TransferInitiatedEvent event) {
        Transfer transfer = loadTransfer(event.transferId());
        if (transfer.getStatus() != TransferStatus.INITIATED) {
            log.debug("Transfer {} already past INITIATED, skipping fraud check", transfer.getId());
            return; // idempotent
        }

        try {
            transfer.markFraudChecking();
            FraudCheckResult result = fraudDetectionPort.check(transfer);
            transfer.markFraudChecked(result);
            transferRepository.save(transfer);
            publishEvents(transfer);

            if (!result.approved()) {
                log.warn("Fraud detected for transfer {}: {}", transfer.getId(), result.reason());
            }
        } catch (Exception ex) {
            failTransfer(transfer, "Fraud check failed: " + ex.getMessage());
        }
    }

    // ── Step 2: FX Conversion ────────────────────────────────────────────

    private void handleFraudCheckCompleted(FraudCheckCompletedEvent event) {
        if (!event.approved()) {
            return; // Transfer already marked FAILED by markFraudChecked
        }

        Transfer transfer = loadTransfer(event.transferId());
        if (transfer.getStatus() != TransferStatus.FX_CONVERTING) {
            log.debug("Transfer {} not in FX_CONVERTING, skipping", transfer.getId());
            return;
        }

        try {
            if (transfer.requiresFxConversion()) {
                ExchangeRate rate = fxRatePort.getRate(
                        transfer.getSourceAmount().currency(),
                        transfer.getTargetCurrency());
                Money converted = rate.convert(transfer.getSourceAmount());
                transfer.markFxConverted(rate, converted);
            } else {
                // Same currency — no conversion needed, use identity rate
                ExchangeRate identity = new ExchangeRate(
                        transfer.getSourceAmount().currency(),
                        transfer.getTargetCurrency(),
                        java.math.BigDecimal.ONE,
                        Instant.now());
                transfer.markFxConverted(identity, transfer.getSourceAmount());
            }
            transferRepository.save(transfer);
            publishEvents(transfer);
        } catch (Exception ex) {
            failTransfer(transfer, "FX conversion failed: " + ex.getMessage());
        }
    }

    // ── Step 3: Debit Source Account ─────────────────────────────────────

    private void handleFxConversionCompleted(FxConversionCompletedEvent event) {
        Transfer transfer = loadTransfer(event.transferId());
        if (transfer.getStatus() != TransferStatus.DEBITING) {
            return;
        }

        try {
            Account source = accountRepository.findById(transfer.getSourceAccountId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Source account not found: " + transfer.getSourceAccountId()));
            source.debit(transfer.getSourceAmount(), transfer.getId());
            accountRepository.save(source);
            transfer.markDebited();
            transferRepository.save(transfer);
            publishEvents(transfer);
            publishEvents(source);
        } catch (Exception ex) {
            failTransfer(transfer, "Debit failed: " + ex.getMessage());
        }
    }

    // ── Step 4: Credit Target Account ────────────────────────────────────

    private void handleAccountDebited(AccountDebitedEvent event) {
        Transfer transfer = transferRepository.findById(event.transferId())
                .orElse(null);
        if (transfer == null || transfer.getStatus() != TransferStatus.CREDITING) {
            return;
        }

        try {
            Money creditAmount = transfer.getTargetAmount() != null
                    ? transfer.getTargetAmount()
                    : transfer.getSourceAmount();
            Account target = accountRepository.findById(transfer.getTargetAccountId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Target account not found: " + transfer.getTargetAccountId()));
            target.credit(creditAmount, transfer.getId());
            accountRepository.save(target);
            transfer.markCredited();
            transferRepository.save(transfer);
            publishEvents(transfer);
            publishEvents(target);
        } catch (Exception ex) {
            // Compensation: re-credit the source account
            compensateDebit(transfer);
            failTransfer(transfer, "Credit failed: " + ex.getMessage());
        }
    }

    // ── Step 5: Record Double-Entry Ledger ───────────────────────────────

    private void handleAccountCredited(AccountCreditedEvent event) {
        Transfer transfer = transferRepository.findById(event.transferId())
                .orElse(null);
        if (transfer == null || transfer.getStatus() != TransferStatus.RECORDING_LEDGER) {
            return;
        }

        try {
            Money ledgerAmount = transfer.getTargetAmount() != null
                    ? transfer.getTargetAmount()
                    : transfer.getSourceAmount();
            LedgerEntry entry = LedgerEntry.create(
                    transfer.getSourceAccountId(),
                    transfer.getTargetAccountId(),
                    ledgerAmount,
                    transfer.getId());
            ledgerRepository.save(entry);

            transfer.markLedgerRecorded();
            transferRepository.save(transfer);
            publishEvents(transfer);

            log.info("Transfer {} completed successfully", transfer.getId());
        } catch (Exception ex) {
            failTransfer(transfer, "Ledger recording failed: " + ex.getMessage());
        }
    }

    // ── Step 6: Completed ────────────────────────────────────────────────

    private void handleLedgerRecorded(LedgerEntryRecordedEvent event) {
        log.info("Ledger entry {} recorded for transfer {}",
                event.ledgerEntryId(), event.transferId());
    }

    // ── Compensation ─────────────────────────────────────────────────────

    /**
     * Compensates a debit by crediting the amount back to the source account.
     */
    private void compensateDebit(Transfer transfer) {
        try {
            transfer.markCompensating();
            Account source = accountRepository.findById(transfer.getSourceAccountId())
                    .orElse(null);
            if (source != null) {
                source.credit(transfer.getSourceAmount(), transfer.getId());
                accountRepository.save(source);
                log.info("Compensated debit for transfer {}", transfer.getId());
            }
        } catch (Exception ex) {
            log.error("CRITICAL: Compensation failed for transfer {}. Manual intervention required.",
                    transfer.getId(), ex);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Transfer loadTransfer(TransferId transferId) {
        return transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalStateException(
                        "Transfer not found: " + transferId));
    }

    private void failTransfer(Transfer transfer, String reason) {
        transfer.markFailed(reason);
        transferRepository.save(transfer);
        publishEvents(transfer);
    }

    /**
     * Publishes pending domain events from an aggregate to the outbox.
     */
    private void publishEvents(com.mariosmant.fintech.domain.model.AggregateRoot aggregate) {
        for (DomainEvent evt : aggregate.getDomainEvents()) {
            outboxRepository.save(OutboxEvent.create(
                    aggregate.getClass().getSimpleName(),
                    evt.aggregateId(),
                    evt.getClass().getSimpleName(),
                    evt.toString()
            ));
        }
        aggregate.clearEvents();
    }
}

