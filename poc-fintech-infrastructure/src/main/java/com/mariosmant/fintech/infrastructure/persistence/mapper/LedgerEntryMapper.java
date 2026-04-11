package com.mariosmant.fintech.infrastructure.persistence.mapper;

import com.mariosmant.fintech.domain.model.LedgerEntry;
import com.mariosmant.fintech.domain.model.vo.*;
import com.mariosmant.fintech.infrastructure.persistence.entity.LedgerEntryJpaEntity;

/**
 * Maps between {@link LedgerEntry} (domain) and {@link LedgerEntryJpaEntity} (JPA).
 *
 * @author mariosmant
 * @since 1.0.0
 */
public final class LedgerEntryMapper {

    private LedgerEntryMapper() { /* utility */ }

    public static LedgerEntry toDomain(LedgerEntryJpaEntity e) {
        return new LedgerEntry(
                new LedgerEntryId(e.getId()),
                new AccountId(e.getDebitAccountId()),
                new AccountId(e.getCreditAccountId()),
                new Money(e.getAmount(), Currency.valueOf(e.getCurrency())),
                new TransferId(e.getTransferId()),
                e.getCreatedAt()
        );
    }

    public static LedgerEntryJpaEntity toEntity(LedgerEntry l) {
        LedgerEntryJpaEntity e = new LedgerEntryJpaEntity();
        e.setId(l.getId().value());
        e.setDebitAccountId(l.getDebitAccountId().value());
        e.setCreditAccountId(l.getCreditAccountId().value());
        e.setAmount(l.getAmount().amount());
        e.setCurrency(l.getAmount().currency().name());
        e.setTransferId(l.getTransferId().value());
        e.setCreatedAt(l.getCreatedAt());
        return e;
    }
}

