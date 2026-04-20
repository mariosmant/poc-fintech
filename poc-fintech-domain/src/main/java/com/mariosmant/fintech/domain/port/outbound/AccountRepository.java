package com.mariosmant.fintech.domain.port.outbound;

import com.mariosmant.fintech.domain.model.Account;
import com.mariosmant.fintech.domain.model.vo.AccountId;

import java.util.List;
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
     * Finds an account by its IBAN (ISO 13616). Used when the client initiates a
     * transfer using a third-party IBAN rather than an internal UUID.
     *
     * @param iban the IBAN (normalised, no whitespace)
     * @return the account, or empty if not found
     */
    Optional<Account> findByIban(String iban);

    /**
     * Returns all accounts ordered by most recently created first.
     *
     * @return all accounts
     */
    List<Account> findAll();

    /**
     * Returns all accounts owned by the given user, ordered by most recently created first.
     * Used to enforce tenant isolation — users can only see their own accounts.
     *
     * @param ownerId the owner's user ID (from JWT sub claim)
     * @return accounts belonging to the specified owner
     */
    List<Account> findByOwnerId(String ownerId);

    /**
     * Persists the given account (insert or update).
     * Optimistic locking is enforced via the version field.
     *
     * @param account the account to save
     * @return the saved account (with updated version)
     */
    Account save(Account account);

    /**
     * Persists the given account with the authenticated user's ID as owner.
     * Used for account creation — ownerId comes from JWT, never from client.
     *
     * @param account the account to save
     * @param ownerId the authenticated user ID (from JWT sub claim)
     * @return the saved account
     */
    default Account save(Account account, String ownerId) {
        return save(account); // default fallback for non-security contexts
    }
}
