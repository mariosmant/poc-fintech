package com.mariosmant.fintech.domain.event;

import com.mariosmant.fintech.domain.model.vo.AccountId;
import com.mariosmant.fintech.domain.model.vo.LedgerEntryId;
import com.mariosmant.fintech.domain.model.vo.TransferId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a double-entry ledger record has been persisted.
 *
 * @author mariosmant
 * @since 1.0.0
 */
public record LedgerEntryRecordedEvent(
        UUID eventId,
        String aggregateId,
        Instant occurredAt,
        LedgerEntryId ledgerEntryId,
        AccountId debitAccountId,
        AccountId creditAccountId,
        BigDecimal amount,
        TransferId transferId
) implements DomainEvent {
}

