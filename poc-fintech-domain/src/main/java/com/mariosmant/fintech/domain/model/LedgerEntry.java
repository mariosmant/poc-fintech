package com.mariosmant.fintech.domain.model;

import com.mariosmant.fintech.domain.model.vo.*;

import java.time.Instant;
import java.util.Objects;

/**
 * Ledger Entry — represents a single line in the double-entry accounting ledger.
 *
 * <p><b>Double-entry accounting invariant:</b> Every financial transaction
 * is recorded as both a debit and a credit of equal amount. The debit side
 * is the source account; the credit side is the target account.</p>
 *
 * <p>This is <em>not</em> an aggregate root — it is created as part of a
 * Transfer saga step and is immutable once persisted.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public class LedgerEntry {

    private final LedgerEntryId id;
    private final AccountId debitAccountId;
    private final AccountId creditAccountId;
    private final Money amount;
    private final TransferId transferId;
    private final Instant createdAt;

    /**
     * Constructs a new ledger entry.
     *
     * @param id              unique identifier
     * @param debitAccountId  the account being debited
     * @param creditAccountId the account being credited
     * @param amount          the amount (must be positive)
     * @param transferId      the associated transfer
     * @param createdAt       when this entry was created
     * @throws IllegalArgumentException if amount is not positive
     */
    public LedgerEntry(LedgerEntryId id, AccountId debitAccountId,
                       AccountId creditAccountId, Money amount,
                       TransferId transferId, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "LedgerEntryId must not be null");
        this.debitAccountId = Objects.requireNonNull(debitAccountId);
        this.creditAccountId = Objects.requireNonNull(creditAccountId);
        this.amount = Objects.requireNonNull(amount);
        this.transferId = Objects.requireNonNull(transferId);
        this.createdAt = Objects.requireNonNull(createdAt);

        // Double-entry invariant: amount must be positive
        if (!amount.isPositive()) {
            throw new IllegalArgumentException(
                    "Ledger entry amount must be positive, got: " + amount);
        }
    }

    /**
     * Factory: creates a new ledger entry for a transfer.
     *
     * @param debitAccountId  source account
     * @param creditAccountId target account
     * @param amount          the amount (positive)
     * @param transferId      the associated transfer
     * @return a new {@code LedgerEntry}
     */
    public static LedgerEntry create(AccountId debitAccountId, AccountId creditAccountId,
                                     Money amount, TransferId transferId) {
        return new LedgerEntry(LedgerEntryId.generate(), debitAccountId,
                creditAccountId, amount, transferId, Instant.now());
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public LedgerEntryId getId() { return id; }
    public AccountId getDebitAccountId() { return debitAccountId; }
    public AccountId getCreditAccountId() { return creditAccountId; }
    public Money getAmount() { return amount; }
    public TransferId getTransferId() { return transferId; }
    public Instant getCreatedAt() { return createdAt; }
}

