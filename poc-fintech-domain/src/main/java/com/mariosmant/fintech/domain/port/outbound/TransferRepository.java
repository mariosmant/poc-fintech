package com.mariosmant.fintech.domain.port.outbound;

import com.mariosmant.fintech.domain.model.Transfer;
import com.mariosmant.fintech.domain.model.vo.IdempotencyKey;
import com.mariosmant.fintech.domain.model.vo.TransferId;

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

    Transfer save(Transfer transfer);
}

