package com.mariosmant.fintech.application.usecase;

import com.mariosmant.fintech.application.dto.LedgerEntryResponse;
import com.mariosmant.fintech.domain.model.LedgerEntry;
import com.mariosmant.fintech.domain.model.vo.AccountId;
import com.mariosmant.fintech.domain.model.vo.TransferId;
import com.mariosmant.fintech.domain.port.outbound.LedgerRepository;

import java.util.List;
import java.util.UUID;

/**
 * Use Case: Query ledger entries (CQRS read side).
 *
 * @author mariosmant
 * @since 1.0.0
 */
public class LedgerQueryUseCase {

    private final LedgerRepository ledgerRepository;

    public LedgerQueryUseCase(LedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    /** Finds all ledger entries for a given transfer. */
    public List<LedgerEntryResponse> findByTransferId(UUID transferId) {
        return ledgerRepository.findByTransferId(new TransferId(transferId))
                .stream().map(this::toResponse).toList();
    }

    /** Finds all ledger entries for a given account. */
    public List<LedgerEntryResponse> findByAccountId(UUID accountId) {
        return ledgerRepository.findByAccountId(new AccountId(accountId))
                .stream().map(this::toResponse).toList();
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

