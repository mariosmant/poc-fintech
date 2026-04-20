package com.mariosmant.fintech.infrastructure.persistence.mapper;

import com.mariosmant.fintech.domain.model.Transfer;
import com.mariosmant.fintech.domain.model.vo.*;
import com.mariosmant.fintech.infrastructure.persistence.entity.TransferJpaEntity;

import java.time.Instant;

/**
 * Maps between {@link Transfer} (domain) and {@link TransferJpaEntity} (JPA).
 *
 * @author mariosmant
 * @since 1.0.0
 */
public final class TransferMapper {

    private TransferMapper() { /* utility */ }

    /** Converts a JPA entity to a domain aggregate. */
    public static Transfer toDomain(TransferJpaEntity e) {
        Currency srcCcy = Currency.valueOf(e.getSourceCurrency());
        Currency tgtCcy = Currency.valueOf(e.getTargetCurrency());

        // targetAmount is null until FX conversion step completes in the saga
        Money targetAmount = e.getTargetAmount() != null
                ? new Money(e.getTargetAmount(), tgtCcy) : null;

        // ExchangeRate is null until FX conversion; on reconstitution we use Instant.now()
        // because the original quote timestamp is not persisted (trade-off: rate table
        // only stores the numeric rate, not the full ExchangeRate value object).
        ExchangeRate rate = e.getExchangeRate() != null
                ? new ExchangeRate(srcCcy, tgtCcy, e.getExchangeRate(), Instant.now())
                : null;

        return new Transfer(
                new TransferId(e.getId()),
                new AccountId(e.getSourceAccountId()),
                new AccountId(e.getTargetAccountId()),
                new Money(e.getSourceAmount(), srcCcy),
                tgtCcy,
                targetAmount,
                rate,
                TransferStatus.valueOf(e.getStatus()),
                new IdempotencyKey(e.getIdempotencyKey()),
                e.getFailureReason(),
                e.getVersion()
        );
    }

    /** Converts a domain aggregate to a JPA entity. */
    public static TransferJpaEntity toEntity(Transfer t) {
        TransferJpaEntity e = new TransferJpaEntity();
        e.setId(t.getId().value());
        e.setSourceAccountId(t.getSourceAccountId().value());
        e.setTargetAccountId(t.getTargetAccountId().value());
        e.setSourceAmount(t.getSourceAmount().amount());
        e.setSourceCurrency(t.getSourceAmount().currency().name());
        e.setTargetAmount(t.getTargetAmount() != null ? t.getTargetAmount().amount() : null);
        e.setTargetCurrency(t.getTargetCurrency().name());
        e.setExchangeRate(t.getExchangeRate() != null ? t.getExchangeRate().rate() : null);
        e.setStatus(t.getStatus().name());
        e.setIdempotencyKey(t.getIdempotencyKey().value());
        e.setFailureReason(t.getFailureReason());
        e.setVersion(t.getVersion());
        return e;
    }
}
