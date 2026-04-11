package com.mariosmant.fintech.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code ledger_entries} table (double-entry accounting).
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntryJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "debit_account_id", nullable = false)
    private UUID debitAccountId;

    @Column(name = "credit_account_id", nullable = false)
    private UUID creditAccountId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public LedgerEntryJpaEntity() {
    }

    // ── Getters & Setters ────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getDebitAccountId() { return debitAccountId; }
    public void setDebitAccountId(UUID debitAccountId) { this.debitAccountId = debitAccountId; }
    public UUID getCreditAccountId() { return creditAccountId; }
    public void setCreditAccountId(UUID creditAccountId) { this.creditAccountId = creditAccountId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public UUID getTransferId() { return transferId; }
    public void setTransferId(UUID transferId) { this.transferId = transferId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}


