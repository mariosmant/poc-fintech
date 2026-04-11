package com.mariosmant.fintech.domain.model.vo;

/**
 * Represents the lifecycle states of a money transfer within the Saga.
 *
 * <p>State transitions follow this happy path:
 * <pre>
 *   INITIATED → FRAUD_CHECKING → FX_CONVERTING → DEBITING
 *   → CREDITING → RECORDING_LEDGER → COMPLETED
 * </pre>
 * Failure at any step transitions to {@code FAILED} (with optional
 * {@code COMPENSATING} for rollback of already-executed steps).</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public enum TransferStatus {

    /** Transfer request received and persisted. */
    INITIATED,
    /** Fraud detection check in progress. */
    FRAUD_CHECKING,
    /** Foreign-exchange conversion in progress. */
    FX_CONVERTING,
    /** Source account debit in progress. */
    DEBITING,
    /** Target account credit in progress. */
    CREDITING,
    /** Double-entry ledger recording in progress. */
    RECORDING_LEDGER,
    /** Transfer completed successfully — terminal state. */
    COMPLETED,
    /** Transfer failed — terminal state. */
    FAILED,
    /** Compensation (rollback) of previous saga steps in progress. */
    COMPENSATING
}

