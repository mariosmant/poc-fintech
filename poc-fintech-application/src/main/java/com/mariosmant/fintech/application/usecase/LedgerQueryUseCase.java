package com.mariosmant.fintech.application.usecase;

import com.mariosmant.fintech.application.dto.LedgerEntryResponse;
import com.mariosmant.fintech.domain.exception.ResourceAccessDeniedException;
import com.mariosmant.fintech.domain.model.Account;
import com.mariosmant.fintech.domain.model.LedgerEntry;
import com.mariosmant.fintech.domain.model.Transfer;
import com.mariosmant.fintech.domain.model.vo.AccountId;
import com.mariosmant.fintech.domain.model.vo.TransferId;
import com.mariosmant.fintech.domain.port.outbound.AccountRepository;
import com.mariosmant.fintech.domain.port.outbound.LedgerRepository;
import com.mariosmant.fintech.domain.port.outbound.TransferRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Use Case: Query ledger entries (CQRS read side).
 *
 * <p>Provides both "admin" (all entries) and "user-scoped" variants. The user-scoped
 * variants filter entries so the caller can only see ledger rows that touch at least
 * one account they own (NIST AC-3, AC-6 — least privilege).</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public class LedgerQueryUseCase {

    private final LedgerRepository ledgerRepository;
    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;

    public LedgerQueryUseCase(LedgerRepository ledgerRepository,
                              AccountRepository accountRepository,
                              TransferRepository transferRepository) {
        this.ledgerRepository = ledgerRepository;
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
    }

    /** Finds all ledger entries for a given transfer (admin / system use). */
    public List<LedgerEntryResponse> findByTransferId(UUID transferId) {
        return ledgerRepository.findByTransferId(new TransferId(transferId))
                .stream().map(this::toResponse).toList();
    }

    /** Finds all ledger entries for a given account (admin / system use). */
    public List<LedgerEntryResponse> findByAccountId(UUID accountId) {
        return ledgerRepository.findByAccountId(new AccountId(accountId))
                .stream().map(this::toResponse).toList();
    }

    /** Returns recent ledger entries for monitoring dashboards (admin / system use). */
    public List<LedgerEntryResponse> findRecent(int limit) {
        return ledgerRepository.findRecent(limit)
                .stream().map(this::toResponse).toList();
    }

    /**
     * Recent ledger entries touching at least one account owned by {@code userId}.
     * Used by the non-admin read path.
     */
    public List<LedgerEntryResponse> findRecentForUser(String userId, int limit) {
        Set<UUID> userAccountIds = userAccountIdSet(userId);
        int overFetch = Math.min(Math.max(limit * 5, limit), 2_000);
        return ledgerRepository.findRecent(overFetch).stream()
                .filter(e -> userAccountIds.contains(e.getDebitAccountId().value())
                        || userAccountIds.contains(e.getCreditAccountId().value()))
                .limit(limit)
                .map(this::toResponse)
                .toList();
    }

    /**
     * Ledger entries for the given account, but only if the caller owns the account.
     * Throws {@link ResourceAccessDeniedException} (mapped to {@code 403}) otherwise.
     */
    public List<LedgerEntryResponse> findByAccountIdForUser(UUID accountId, String userId) {
        boolean ownsAccount = userAccountIdSet(userId).contains(accountId);
        if (!ownsAccount) {
            throw new ResourceAccessDeniedException("Account is not visible to the current user");
        }
        return findByAccountId(accountId);
    }

    /**
     * Ledger entries for the given transfer, but only if the caller owns at least one
     * of the transfer's accounts. Throws {@link ResourceAccessDeniedException} otherwise.
     */
    public List<LedgerEntryResponse> findByTransferIdForUser(UUID transferId, String userId) {
        Transfer t = transferRepository.findById(new TransferId(transferId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transfer not found: " + transferId));
        Set<UUID> userAccountIds = userAccountIdSet(userId);
        boolean visible = userAccountIds.contains(t.getSourceAccountId().value())
                || userAccountIds.contains(t.getTargetAccountId().value());
        if (!visible) {
            throw new ResourceAccessDeniedException("Transfer is not visible to the current user");
        }
        return findByTransferId(transferId);
    }

    private Set<UUID> userAccountIdSet(String userId) {
        Set<UUID> ids = new HashSet<>();
        for (Account a : accountRepository.findByOwnerId(userId)) {
            ids.add(a.getId().value());
        }
        return ids;
    }

    private LedgerEntryResponse toResponse(LedgerEntry e) {
        return new LedgerEntryResponse(
                e.getId().value(),
                e.getDebitAccountId().value(),
                e.getCreditAccountId().value(),
                e.getAmount().amount(),
                e.getAmount().currency().name(),
                e.getTransferId().value(),
                e.getCreatedAt()
        );
    }
}

