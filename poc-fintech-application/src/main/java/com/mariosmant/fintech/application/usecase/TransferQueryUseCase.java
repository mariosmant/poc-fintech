package com.mariosmant.fintech.application.usecase;

import com.mariosmant.fintech.application.dto.TransferResponse;
import com.mariosmant.fintech.domain.model.Account;
import com.mariosmant.fintech.domain.model.Transfer;
import com.mariosmant.fintech.domain.model.vo.TransferId;
import com.mariosmant.fintech.domain.port.outbound.AccountRepository;
import com.mariosmant.fintech.domain.port.outbound.TransferRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Use Case: Query transfer details (CQRS read side).
 * Enriches raw {@link Transfer} aggregates with source/target IBANs for the UI.
 *
 * @author mariosmant
 * @since 1.0.0
 */
public class TransferQueryUseCase {

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;

    public TransferQueryUseCase(TransferRepository transferRepository,
                                AccountRepository accountRepository) {
        this.transferRepository = transferRepository;
        this.accountRepository = accountRepository;
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
        Map<UUID, String> ibans = loadIbans(List.of(t));
        return toResponse(t, ibans);
    }

    /** Returns the latest transfers for monitoring screens. */
    public List<TransferResponse> findLatest(int limit) {
        List<Transfer> transfers = transferRepository.findLatest(limit);
        Map<UUID, String> ibans = loadIbans(transfers);
        return transfers.stream().map(t -> toResponse(t, ibans)).toList();
    }

    private Map<UUID, String> loadIbans(List<Transfer> transfers) {
        Map<UUID, String> out = new HashMap<>();
        for (Transfer t : transfers) {
            cachePut(out, t.getSourceAccountId().value());
            cachePut(out, t.getTargetAccountId().value());
        }
        return out;
    }

    private void cachePut(Map<UUID, String> cache, UUID id) {
        cache.computeIfAbsent(id, key ->
                accountRepository.findById(new com.mariosmant.fintech.domain.model.vo.AccountId(key))
                        .map(Account::getIban).orElse(null));
    }

    private TransferResponse toResponse(Transfer t, Map<UUID, String> ibans) {
        return new TransferResponse(
                t.getId().value(),
                t.getStatus().name(),
                t.getSourceAccountId().value(),
                ibans.get(t.getSourceAccountId().value()),
                t.getTargetAccountId().value(),
                ibans.get(t.getTargetAccountId().value()),
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
