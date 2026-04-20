package com.mariosmant.fintech.domain.port.outbound;

import com.mariosmant.fintech.domain.model.Transfer;
import com.mariosmant.fintech.domain.model.vo.IdempotencyKey;
import com.mariosmant.fintech.domain.model.vo.TransferId;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for Transfer persistence.
 *
 * @author mariosmant
 * @since 1.0.0
 */
public interface TransferRepository {

    Optional<Transfer> findById(TransferId id);

    /**
     * Finds a transfer by its idempotency key (for duplicate detection).
     *
     * @param key the idempotency key
     * @return the existing transfer, or empty
     */
    Optional<Transfer> findByIdempotencyKey(IdempotencyKey key);

    /**
     * Returns most recent transfers ordered by creation time descending.
     *
     * @param limit max number of transfers to return
     * @return recent transfers
     */
    List<Transfer> findLatest(int limit);

    Transfer save(Transfer transfer);

    /**
     * Persists the given transfer with the authenticated user's ID as initiator.
     *
     * @param transfer    the transfer to save
     * @param initiatedBy the authenticated user ID (from JWT sub claim)
     * @return the saved transfer
     */
    default Transfer save(Transfer transfer, String initiatedBy) {
        return save(transfer);
    }
}

