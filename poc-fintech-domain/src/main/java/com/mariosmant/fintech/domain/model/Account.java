package com.mariosmant.fintech.domain.model;

import com.mariosmant.fintech.domain.event.AccountCreditedEvent;
import com.mariosmant.fintech.domain.event.AccountDebitedEvent;
import com.mariosmant.fintech.domain.exception.InsufficientFundsException;
import com.mariosmant.fintech.domain.model.vo.AccountId;
import com.mariosmant.fintech.domain.model.vo.Currency;
import com.mariosmant.fintech.domain.model.vo.Money;
import com.mariosmant.fintech.domain.model.vo.TransferId;
import com.mariosmant.fintech.domain.util.IbanUtil;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * {@code Account} — <b>Aggregate Root</b> of the Account bounded context.
 *
 * <h2>Architecture role</h2>
 * <ul>
 *   <li><b>DDD Aggregate Root:</b> the single consistency boundary for its
 *       internal state ({@code balance}, {@code version}). All mutations go
 *       through this class — the persistence adapter reconstitutes it from
 *       the {@code accounts} row (+ owner_id) and persists it back atomically
 *       (DDD — Evans, ch. 6).</li>
 *   <li><b>Hexagonal Architecture — inside the domain hexagon:</b> no Spring,
 *       no JPA annotations, no framework coupling. Persistence is injected
 *       through the outbound {@code AccountRepository} port.</li>
 *   <li><b>Optimistic concurrency:</b> the {@code version} field is mapped to
 *       a JPA {@code @Version} column in the infrastructure layer — two
 *       concurrent transfers on the same account will cause one to fail with
 *       {@code OptimisticLockException}, which the Saga Orchestrator turns
 *       into a compensation step (no deadlocks, no row locks).</li>
 *   <li><b>Event Sourcing (lightweight):</b> every balance mutation
 *       {@link #registerEvent registers} a domain event on the aggregate.
 *       The application layer drains these events into the Transactional
 *       Outbox so that downstream read models (ledger, notifications) stay
 *       eventually consistent without dual writes.</li>
 * </ul>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>Balance must never go negative — {@link #debit} throws
 *       {@link InsufficientFundsException} before mutating state.</li>
 *   <li>Every mutation emits exactly one corresponding domain event.</li>
 *   <li>Currency match is enforced on both debit and credit — cross-currency
 *       transfers go through the FX conversion step of the Saga first, so by
 *       the time this aggregate is touched amounts are already in its own
 *       currency.</li>
 * </ul>
 *
 * <h2>PCI DSS / NIST touchpoints</h2>
 * <ul>
 *   <li><b>PCI DSS §3.3 analogue</b>: {@link #iban} is sensitive data;
 *       callers rendering it in UI / logs must route through
 *       {@link com.mariosmant.fintech.domain.util.IbanMasking}.</li>
 *   <li><b>NIST AU-2</b>: mutations cross into {@code @Audited} use-case
 *       handlers in the application layer, which produce the audit trail.</li>
 *   <li><b>Ownership</b>: {@code owner_id} is persisted separately (migration
 *       V6) and is set from the JWT {@code sub} claim at use-case entry —
 *       never from client input (OWASP API1: Broken Object-Level Auth).</li>
 * </ul>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public class Account extends AggregateRoot {

    private final AccountId id;
    private final String iban;
    private final String ownerName;
    private Money balance;
    private int version;

    /**
     * Reconstitution constructor used by the persistence layer.
     *
     * @param id        the account identifier
     * @param iban      the account's IBAN (ISO 13616, mod-97 valid)
     * @param ownerName the account owner's name
     * @param balance   the current balance
     * @param version   the optimistic-lock version
     */
    public Account(AccountId id, String iban, String ownerName, Money balance, int version) {
        this.id = Objects.requireNonNull(id, "Account ID must not be null");
        this.iban = Objects.requireNonNull(iban, "IBAN must not be null");
        this.ownerName = Objects.requireNonNull(ownerName, "Owner name must not be null");
        this.balance = Objects.requireNonNull(balance, "Balance must not be null");
        this.version = version;
    }

    /**
     * Factory: creates a new account with zero balance in the given currency.
     * IBAN is deterministically generated from the new account UUID.
     */
    public static Account create(String ownerName, Currency currency) {
        AccountId newId = AccountId.generate();
        return new Account(newId, IbanUtil.generate(newId.value()), ownerName, Money.zero(currency), 0);
    }

    /**
     * Factory: creates a new account with a specified initial balance.
     * IBAN is deterministically generated from the new account UUID.
     */
    public static Account createWithBalance(String ownerName, Money initialBalance) {
        AccountId newId = AccountId.generate();
        return new Account(newId, IbanUtil.generate(newId.value()), ownerName, initialBalance, 0);
    }

    /**
     * Debits (withdraws) the specified amount from this account.
     *
     * @param amount     the amount to debit; must be positive and in the same currency
     * @param transferId the transfer causing this debit (for event correlation)
     * @throws InsufficientFundsException if the balance would go negative
     * @throws IllegalArgumentException   if amount is not positive or currency mismatches
     */
    public void debit(Money amount, TransferId transferId) {
        validatePositiveAmount(amount);
        if (balance.subtract(amount).isNegative()) {
            throw new InsufficientFundsException(
                    "Account %s has insufficient funds: balance=%s, requested=%s"
                            .formatted(id, balance, amount));
        }
        balance = balance.subtract(amount);
        registerEvent(new AccountDebitedEvent(
                UUID.randomUUID(),
                id.toString(),
                Instant.now(),
                id,
                transferId,
                amount.amount()
        ));
    }

    /**
     * Credits (deposits) the specified amount to this account.
     *
     * @param amount     the amount to credit; must be positive
     * @param transferId the transfer causing this credit (for event correlation)
     */
    public void credit(Money amount, TransferId transferId) {
        validatePositiveAmount(amount);
        balance = balance.add(amount);
        registerEvent(new AccountCreditedEvent(
                UUID.randomUUID(),
                id.toString(),
                Instant.now(),
                id,
                transferId,
                amount.amount()
        ));
    }

    private void validatePositiveAmount(Money amount) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Amount must be positive: " + amount);
        }
        if (amount.currency() != balance.currency()) {
            throw new IllegalArgumentException(
                    "Currency mismatch: account=%s, amount=%s"
                            .formatted(balance.currency(), amount.currency()));
        }
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public AccountId getId() { return id; }
    public String getIban() { return iban; }
    public String getOwnerName() { return ownerName; }
    public Money getBalance() { return balance; }
    public int getVersion() { return version; }
}

