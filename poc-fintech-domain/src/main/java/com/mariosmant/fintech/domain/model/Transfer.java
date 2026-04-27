package com.mariosmant.fintech.domain.model;

import com.mariosmant.fintech.domain.event.FraudCheckCompletedEvent;
import com.mariosmant.fintech.domain.event.FxConversionCompletedEvent;
import com.mariosmant.fintech.domain.event.TransferCompletedEvent;
import com.mariosmant.fintech.domain.event.TransferFailedEvent;
import com.mariosmant.fintech.domain.event.TransferInitiatedEvent;
import com.mariosmant.fintech.domain.exception.InvalidTransferStateException;
import com.mariosmant.fintech.domain.model.vo.AccountId;
import com.mariosmant.fintech.domain.model.vo.Currency;
import com.mariosmant.fintech.domain.model.vo.ExchangeRate;
import com.mariosmant.fintech.domain.model.vo.FraudCheckResult;
import com.mariosmant.fintech.domain.model.vo.IdempotencyKey;
import com.mariosmant.fintech.domain.model.vo.Money;
import com.mariosmant.fintech.domain.model.vo.TransferId;
import com.mariosmant.fintech.domain.model.vo.TransferStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * {@code Transfer} — <b>Aggregate Root</b> modelling a money movement between
 * two accounts as a finite state machine driven by the {@code TransferSagaOrchestrator}.
 *
 * <h2>Architecture role</h2>
 * <ul>
 *   <li><b>Saga pattern (orchestrated):</b> each state transition corresponds
 *       to one step of the transfer saga — {@code INITIATED → FRAUD_CHECKING →
 *       FX_CONVERTING → DEBITING → CREDITING → RECORDING_LEDGER → COMPLETED},
 *       with {@code COMPENSATING}/{@code FAILED} as the reverse path. The
 *       orchestrator picks the next step purely from the current status,
 *       which keeps the saga stateless on the application side and the state
 *       machine testable in isolation. (Ref: Richardson — Microservices Patterns
 *       ch. 4, Sagas — Orchestration variant.)</li>
 *   <li><b>Idempotency:</b> {@link #assertStatus} ensures that replaying a
 *       Kafka event after a crash (at-least-once delivery) is a no-op if the
 *       transfer has already advanced past the corresponding step. Combined
 *       with the producer-side Transactional Outbox this yields <i>effectively
 *       once</i> processing.</li>
 *   <li><b>Business idempotency key:</b> {@link #idempotencyKey} is
 *       client-supplied and enforced as a unique constraint at the persistence
 *       layer — a retried {@code POST /api/v1/transfers} never creates a
 *       duplicate transfer even if the client times out.</li>
 *   <li><b>Optimistic locking:</b> {@link #version} maps to JPA
 *       {@code @Version}; contending updates during compensation scenarios
 *       fail-fast rather than blocking.</li>
 * </ul>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>State transitions must follow the saga step order (guarded by
 *       {@link #assertStatus}).</li>
 *   <li>{@link #targetAmount} / {@link #exchangeRate} are populated only
 *       once FX conversion has completed, and never mutated afterwards.</li>
 *   <li>{@link #markFailed} is terminal and sets {@link #failureReason} so
 *       compensation handlers have the full context for audit logging.</li>
 * </ul>
 *
 * <h2>Fintech concepts realised here</h2>
 * <ul>
 *   <li><b>Multi-currency</b> via {@link #requiresFxConversion} — same-currency
 *       transfers skip the FX step entirely, avoiding unnecessary 1.0-rate
 *       multiplication.</li>
 *   <li><b>Fraud detection</b> runs <i>before</i> any balance mutation — the
 *       debit never happens when fraud is detected (safer than post-hoc
 *       compensation and aligned with PSD2 SCA risk-based authentication).</li>
 *   <li><b>Ledger posting</b> is the last step before {@code COMPLETED}, which
 *       guarantees that a transfer visible as "COMPLETED" always has a
 *       corresponding balanced debit/credit pair in {@code ledger_entries}.</li>
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

