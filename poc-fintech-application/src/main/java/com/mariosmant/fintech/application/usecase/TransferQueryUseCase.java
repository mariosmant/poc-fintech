package com.mariosmant.fintech.application.usecase;

import com.mariosmant.fintech.application.dto.TransferResponse;
import com.mariosmant.fintech.domain.model.Transfer;
import com.mariosmant.fintech.domain.model.vo.TransferId;
import com.mariosmant.fintech.domain.port.outbound.TransferRepository;

import java.util.List;
import java.util.UUID;

/**
 * Use Case: Query transfer details (CQRS read side).
 *
 * @author mariosmant
 * @since 1.0.0
 */
public class TransferQueryUseCase {

    private final TransferRepository transferRepository;

    public TransferQueryUseCase(TransferRepository transferRepository) {
        this.transferRepository = transferRepository;
    }

    /**
     * Retrieves a transfer by its unique identifier.
     *
     * @param transferId the transfer UUID
     * @return the transfer DTO
     * @throws IllegalArgumentException if not found
     */
    public TransferResponse findById(UUID transferId) {
        Transfer t = transferRepository.findById(new TransferId(transferId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transfer not found: " + transferId));
        return toResponse(t);
    }

    /** Returns the latest transfers for monitoring screens. */
    public List<TransferResponse> findLatest(int limit) {
        return transferRepository.findLatest(limit).stream().map(this::toResponse).toList();
    }

    private TransferResponse toResponse(Transfer t) {
        return new TransferResponse(
                t.getId().value(),
                t.getStatus().name(),
                t.getSourceAccountId().value(),
                t.getTargetAccountId().value(),
                t.getSourceAmount().amount(),
                t.getSourceAmount().currency().name(),
                t.getTargetAmount() != null ? t.getTargetAmount().amount() : null,
                t.getTargetCurrency().name(),
                t.getExchangeRate() != null ? t.getExchangeRate().rate() : null,
                t.getFailureReason(),
                t.getIdempotencyKey().value()
        );
    }
}

