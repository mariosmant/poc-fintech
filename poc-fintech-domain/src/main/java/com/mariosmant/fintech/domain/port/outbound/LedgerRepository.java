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

    LedgerEntry save(LedgerEntry entry);

    List<LedgerEntry> findByTransferId(TransferId transferId);

    List<LedgerEntry> findByAccountId(AccountId accountId);

    /**
     * Returns latest ledger entries ordered by creation time descending.
     *
     * @param limit max number of entries to return
     * @return recent ledger entries
     */
    List<LedgerEntry> findRecent(int limit);
}

