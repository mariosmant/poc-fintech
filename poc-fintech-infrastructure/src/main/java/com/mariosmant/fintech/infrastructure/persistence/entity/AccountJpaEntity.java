package com.mariosmant.fintech.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code accounts} table.
 *
 * <p>Uses {@code @Version} for optimistic locking and Spring Data
 * JPA auditing annotations for automatic timestamp management.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Entity
@Table(name = "accounts")
@EntityListeners(AuditingEntityListener.class)
public class AccountJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "iban", nullable = false, unique = true, length = 34)
    private String iban;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    @Column(name = "owner_id")
    private String ownerId;

    @Column(name = "balance_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAmount;

    @Column(name = "balance_currency", nullable = false, length = 3)
    private String balanceCurrency;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Default constructor for JPA. */
    protected AccountJpaEntity() {
    }

    public AccountJpaEntity(UUID id, String iban, String ownerName, BigDecimal balanceAmount,
                            String balanceCurrency, int version) {
        this(id, iban, ownerName, null, balanceAmount, balanceCurrency, version);
    }

    public AccountJpaEntity(UUID id, String iban, String ownerName, String ownerId,
                            BigDecimal balanceAmount, String balanceCurrency, int version) {
        this.id = id;
        this.iban = iban;
        this.ownerName = ownerName;
        this.ownerId = ownerId;
        this.balanceAmount = balanceAmount;
        this.balanceCurrency = balanceCurrency;
        this.version = version;
    }

    // ── Getters & Setters ────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getIban() { return iban; }
    public void setIban(String iban) { this.iban = iban; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public BigDecimal getBalanceAmount() { return balanceAmount; }
    public void setBalanceAmount(BigDecimal balanceAmount) { this.balanceAmount = balanceAmount; }
    public String getBalanceCurrency() { return balanceCurrency; }
    public void setBalanceCurrency(String balanceCurrency) { this.balanceCurrency = balanceCurrency; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

