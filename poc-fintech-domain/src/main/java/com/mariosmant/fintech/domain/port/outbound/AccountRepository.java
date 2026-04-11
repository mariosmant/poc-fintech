package com.mariosmant.fintech.domain.port.outbound;

import com.mariosmant.fintech.domain.model.Account;
import com.mariosmant.fintech.domain.model.vo.AccountId;

import java.util.Optional;

/**
 * Outbound port for Account persistence.
 *
 * <p>Implemented by the infrastructure layer (JPA adapter).
 * The domain layer depends only on this interface — hexagonal inversion.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public interface AccountRepository {

    /**
     * Finds an account by its unique identifier.
     *
     * @param id the account ID
     * @return the account, or empty if not found
     */
    Optional<Account> findById(AccountId id);

    /**
     * Persists the given account (insert or update).
     * Optimistic locking is enforced via the version field.
     *
     * @param account the account to save
     * @return the saved account (with updated version)
     */
    Account save(Account account);
}

