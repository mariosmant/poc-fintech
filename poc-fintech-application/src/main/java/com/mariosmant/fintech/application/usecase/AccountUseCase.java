package com.mariosmant.fintech.application.usecase;

import com.mariosmant.fintech.application.command.CreateAccountCommand;
import com.mariosmant.fintech.application.dto.AccountResponse;
import com.mariosmant.fintech.domain.model.Account;
import com.mariosmant.fintech.domain.model.vo.AccountId;
import com.mariosmant.fintech.domain.model.vo.Currency;
import com.mariosmant.fintech.domain.model.vo.Money;
import com.mariosmant.fintech.domain.port.outbound.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * Use Case: Account management (CQRS — handles both create and query).
 *
 * @author mariosmant
 * @since 1.0.0
 */
public class AccountUseCase {

    private static final Logger log = LoggerFactory.getLogger(AccountUseCase.class);

    private final AccountRepository accountRepository;

    public AccountUseCase(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Creates a new account.
     *
     * @param command the create account command
     * @return the created account DTO
     */
    public AccountResponse create(CreateAccountCommand command) {
        var currency = Currency.valueOf(command.currency());
        var initialBalance = new Money(command.initialBalance(), currency);
        var account = Account.createWithBalance(command.ownerName(), initialBalance);
        Account saved = accountRepository.save(account);

        log.info("Account created: id={}, owner={}, currency={}",
                saved.getId(), command.ownerName(), currency);

        return toResponse(saved);
    }

    /**
     * Retrieves an account by ID (CQRS query side).
     *
     * @param accountId the account UUID
     * @return the account DTO
     * @throws IllegalArgumentException if not found
     */
    public AccountResponse findById(UUID accountId) {
        var account = accountRepository.findById(new AccountId(accountId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Account not found: " + accountId));
        return toResponse(account);
    }

    /** Returns all accounts ordered by most recent first. */
    public List<AccountResponse> findAll() {
        return accountRepository.findAll().stream().map(this::toResponse).toList();
    }

    private AccountResponse toResponse(Account a) {
        return new AccountResponse(
                a.getId().value(),
                a.getOwnerName(),
                a.getBalance().amount(),
                a.getBalance().currency().name()
        );
    }
}

