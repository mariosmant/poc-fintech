package com.mariosmant.fintech.domain.port.outbound;

import com.mariosmant.fintech.domain.model.LedgerEntry;
import com.mariosmant.fintech.domain.model.vo.AccountId;
import com.mariosmant.fintech.domain.model.vo.TransferId;

import java.util.List;

/**
 * Outbound port for Ledger persistence (double-entry accounting).
 *
 * @author mariosmant
 * @since 1.0.0
 */
public interface LedgerRepository {

    /**
     * Persists a new ledger entry. Ledger entries are immutable once saved.
     *
     * @param entry the ledger entry to save
     * @return the saved ledger entry
     */
    LedgerEntry save(LedgerEntry entry);

    /**
     * Finds all ledger entries associated with a specific transfer.
     * A completed transfer will have exactly one entry (debit + credit pair).
     *
     * @param transferId the transfer ID to search by
     * @return list of ledger entries for the transfer
     */
    List<LedgerEntry> findByTransferId(TransferId transferId);

    /**
     * Finds all ledger entries where the account appears as either the debit
     * or credit side, ordered by creation time descending.
     *
     * @param accountId the account ID to search by
     * @return list of ledger entries involving the account
     */
    List<LedgerEntry> findByAccountId(AccountId accountId);

    /**
     * Returns latest ledger entries ordered by creation time descending.
     *
     * @param limit max number of entries to return
     * @return recent ledger entries
     */
    List<LedgerEntry> findRecent(int limit);
}
