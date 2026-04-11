package com.mariosmant.fintech.domain.model;

import com.mariosmant.fintech.domain.event.*;
import com.mariosmant.fintech.domain.exception.InvalidTransferStateException;
import com.mariosmant.fintech.domain.model.vo.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Transfer Aggregate Root — represents a money movement between two accounts.
 *
 * <p>Follows a strict state-machine enforced by the Saga Orchestrator.
 * Each state transition validates the legal predecessor state and registers
 * the corresponding domain event.</p>
 *
 * <p><b>Invariants:</b></p>
 * <ul>
 *   <li>State transitions must follow the defined Saga step order.</li>
 *   <li>Idempotency key is unique across the system.</li>
 *   <li>Optimistic locking via {@code version}.</li>
 * </ul>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public class Transfer extends AggregateRoot {

    private final TransferId id;
    private final AccountId sourceAccountId;
    private final AccountId targetAccountId;
    private final Money sourceAmount;
    private final IdempotencyKey idempotencyKey;
    private final Currency targetCurrency;

    private Money targetAmount;
    private ExchangeRate exchangeRate;
    private TransferStatus status;
    private String failureReason;
    private int version;

    /**
     * Full reconstitution constructor (used by persistence adapter).
     */
    public Transfer(TransferId id, AccountId sourceAccountId, AccountId targetAccountId,
                    Money sourceAmount, Currency targetCurrency, Money targetAmount,
                    ExchangeRate exchangeRate, TransferStatus status,
                    IdempotencyKey idempotencyKey, String failureReason, int version) {
        this.id = Objects.requireNonNull(id);
        this.sourceAccountId = Objects.requireNonNull(sourceAccountId);
        this.targetAccountId = Objects.requireNonNull(targetAccountId);
        this.sourceAmount = Objects.requireNonNull(sourceAmount);
        this.targetCurrency = Objects.requireNonNull(targetCurrency);
        this.targetAmount = targetAmount;
        this.exchangeRate = exchangeRate;
        this.status = Objects.requireNonNull(status);
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.failureReason = failureReason;
        this.version = version;
    }

    /**
     * Factory: initiates a new transfer. Status is set to {@link TransferStatus#INITIATED}
     * and a {@link TransferInitiatedEvent} is registered.
     *
     * @param sourceAccountId the debiting account
     * @param targetAccountId the crediting account
     * @param sourceAmount    the amount in the source currency
     * @param targetCurrency  the desired target currency
     * @param idempotencyKey  the client-supplied idempotency key
     * @return a new {@code Transfer} in INITIATED status
     */
    public static Transfer initiate(AccountId sourceAccountId, AccountId targetAccountId,
                                    Money sourceAmount, Currency targetCurrency,
                                    IdempotencyKey idempotencyKey) {
        var transfer = new Transfer(
                TransferId.generate(), sourceAccountId, targetAccountId,
                sourceAmount, targetCurrency, null, null,
                TransferStatus.INITIATED, idempotencyKey, null, 0);

        transfer.registerEvent(new TransferInitiatedEvent(
                UUID.randomUUID(),
                transfer.id.toString(),
                Instant.now(),
                transfer.id,
                sourceAccountId,
                targetAccountId,
                sourceAmount.amount(),
                sourceAmount.currency(),
                targetCurrency,
                idempotencyKey.value()
        ));
        return transfer;
    }

    // ── State transitions ────────────────────────────────────────────────

    /**
     * Transitions to {@code FRAUD_CHECKING}.
     */
    public void markFraudChecking() {
        assertStatus(TransferStatus.INITIATED);
        this.status = TransferStatus.FRAUD_CHECKING;
    }

    /**
     * Marks fraud check as completed.
     *
     * @param result the fraud check result
     */
    public void markFraudChecked(FraudCheckResult result) {
        assertStatus(TransferStatus.FRAUD_CHECKING);
        if (!result.approved()) {
            markFailed("Fraud detected: " + result.reason());
            return;
        }
        this.status = TransferStatus.FX_CONVERTING;
        registerEvent(new FraudCheckCompletedEvent(
                UUID.randomUUID(), id.toString(), Instant.now(),
                id, true, result.reason(), result.riskScore()));
    }

    /**
     * Records the FX conversion result and transitions to DEBITING.
     *
     * @param rate            the exchange rate applied
     * @param convertedAmount the converted target amount
     */
    public void markFxConverted(ExchangeRate rate, Money convertedAmount) {
        assertStatus(TransferStatus.FX_CONVERTING);
        this.exchangeRate = rate;
        this.targetAmount = convertedAmount;
        this.status = TransferStatus.DEBITING;
        registerEvent(new FxConversionCompletedEvent(
                UUID.randomUUID(), id.toString(), Instant.now(),
                id, rate.rate(), convertedAmount.amount(), convertedAmount.currency()));
    }

    /**
     * Marks the source account as debited — transitions to CREDITING.
     */
    public void markDebited() {
        assertStatus(TransferStatus.DEBITING);
        this.status = TransferStatus.CREDITING;
    }

    /**
     * Marks the target account as credited — transitions to RECORDING_LEDGER.
     */
    public void markCredited() {
        assertStatus(TransferStatus.CREDITING);
        this.status = TransferStatus.RECORDING_LEDGER;
    }

    /**
     * Marks the ledger entry as recorded — transitions to COMPLETED.
     */
    public void markLedgerRecorded() {
        assertStatus(TransferStatus.RECORDING_LEDGER);
        this.status = TransferStatus.COMPLETED;
        registerEvent(new TransferCompletedEvent(
                UUID.randomUUID(), id.toString(), Instant.now(), id));
    }

    /**
     * Marks this transfer as failed with the given reason — terminal state.
     *
     * @param reason the failure reason
     */
    public void markFailed(String reason) {
        this.status = TransferStatus.FAILED;
        this.failureReason = reason;
        registerEvent(new TransferFailedEvent(
                UUID.randomUUID(), id.toString(), Instant.now(), id, reason));
    }

    /**
     * Marks this transfer as compensating (rollback in progress).
     */
    public void markCompensating() {
        this.status = TransferStatus.COMPENSATING;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void assertStatus(TransferStatus expected) {
        if (this.status != expected) {
            throw new InvalidTransferStateException(
                    "Transfer %s: expected status %s but was %s"
                            .formatted(id, expected, this.status));
        }
    }

    /** @return {@code true} if the transfer is in a terminal state */
    public boolean isTerminal() {
        return status == TransferStatus.COMPLETED || status == TransferStatus.FAILED;
    }

    /** @return {@code true} if source and target currencies differ */
    public boolean requiresFxConversion() {
        return sourceAmount.currency() != targetCurrency;
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public TransferId getId() { return id; }
    public AccountId getSourceAccountId() { return sourceAccountId; }
    public AccountId getTargetAccountId() { return targetAccountId; }
    public Money getSourceAmount() { return sourceAmount; }
    public Currency getTargetCurrency() { return targetCurrency; }
    public Money getTargetAmount() { return targetAmount; }
    public ExchangeRate getExchangeRate() { return exchangeRate; }
    public TransferStatus getStatus() { return status; }
    public IdempotencyKey getIdempotencyKey() { return idempotencyKey; }
    public String getFailureReason() { return failureReason; }
    public int getVersion() { return version; }
}

