package com.mariosmant.fintech.infrastructure.persistence.mapper;

import com.mariosmant.fintech.domain.model.Account;
import com.mariosmant.fintech.domain.model.vo.AccountId;
import com.mariosmant.fintech.domain.model.vo.Currency;
import com.mariosmant.fintech.domain.model.vo.Money;
import com.mariosmant.fintech.infrastructure.persistence.entity.AccountJpaEntity;

/**
 * Maps between {@link Account} (domain) and {@link AccountJpaEntity} (JPA).
 *
 * @author mariosmant
 * @since 1.0.0
 */
public final class AccountMapper {

    private AccountMapper() { /* utility */ }

    /** Converts a JPA entity to a domain aggregate. */
    public static Account toDomain(AccountJpaEntity e) {
        return new Account(
                new AccountId(e.getId()),
                e.getIban(),
                e.getOwnerName(),
                new Money(e.getBalanceAmount(), Currency.valueOf(e.getBalanceCurrency())),
                e.getVersion()
        );
    }

    /** Converts a domain aggregate to a JPA entity. */
    public static AccountJpaEntity toEntity(Account a) {
        return new AccountJpaEntity(
                a.getId().value(),
                a.getIban(),
                a.getOwnerName(),
                a.getBalance().amount(),
                a.getBalance().currency().name(),
                a.getVersion()
        );
    }
}
