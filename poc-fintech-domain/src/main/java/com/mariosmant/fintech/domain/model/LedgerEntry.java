package com.mariosmant.fintech.domain.model;

import com.mariosmant.fintech.domain.model.vo.*;

import java.time.Instant;
import java.util.Objects;

/**
 * {@code LedgerEntry} — a single row in the double-entry accounting ledger.
 *
 * <h2>Architecture role</h2>
 * <ul>
 *   <li><b>Double-entry bookkeeping</b> (Luca Pacioli, 1494; still the
 *       generally-accepted model in every regulated jurisdiction): every
 *       economic event is recorded as a matched pair of debit and credit of
 *       <i>equal</i> magnitude. The sum of all debits must equal the sum of
 *       all credits across the ledger at all times — this is an auditable
 *       invariant that the application exposes as a reconciliation query.</li>
 *   <li><b>DDD Entity (not an Aggregate Root):</b> a ledger entry is always
 *       created inside the {@code Transfer} saga — its lifecycle is owned by
 *       the transfer that produced it. Once inserted it is <b>immutable</b>
 *       (no setters, no public mutating API; the {@code ledger_entries} table
 *       has no UPDATE/DELETE in any repository method).</li>
 *   <li><b>Event Sourcing flavour:</b> the ledger <i>is</i> the source of
 *       truth for account balance history; account-balance snapshots on the
 *       {@code accounts} table are a materialised view for read performance.
 *       Any balance can be reconstructed from {@code SUM(credits) -
 *       SUM(debits)} over {@code ledger_entries}.</li>
 * </ul>
 *
 * <h2>PCI DSS / audit touchpoints</h2>
 * <ul>
 *   <li><b>PCI DSS §10.5.2 analogue</b> — the ledger is append-only at the
 *       application level, and the adjacent {@code audit_log} table gains
 *       DB-level immutability triggers (Flyway V9) so even an SQL-injection
 *       foothold cannot alter the trail.</li>
 *   <li><b>NIST SP 800-53 AU-10 (non-repudiation)</b> — every
 *       {@code LedgerEntry} carries the {@link #transferId}, which links back
 *       to the {@code initiated_by} user on {@code transfers} and thereby to
 *       the audited JWT subject.</li>
 * </ul>
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

