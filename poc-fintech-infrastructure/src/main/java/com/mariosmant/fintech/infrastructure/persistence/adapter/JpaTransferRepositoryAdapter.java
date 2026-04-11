package com.mariosmant.fintech.infrastructure.persistence.adapter;

import com.mariosmant.fintech.domain.model.Transfer;
import com.mariosmant.fintech.domain.model.vo.IdempotencyKey;
import com.mariosmant.fintech.domain.model.vo.TransferId;
import com.mariosmant.fintech.domain.port.outbound.TransferRepository;
import com.mariosmant.fintech.infrastructure.persistence.mapper.TransferMapper;
import com.mariosmant.fintech.infrastructure.persistence.repository.SpringDataTransferRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * JPA adapter implementing the {@link TransferRepository} outbound port.
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Component
@Transactional
public class JpaTransferRepositoryAdapter implements TransferRepository {

    private final SpringDataTransferRepository jpaRepo;

    public JpaTransferRepositoryAdapter(SpringDataTransferRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Transfer> findById(TransferId id) {
        return jpaRepo.findById(id.value()).map(TransferMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Transfer> findByIdempotencyKey(IdempotencyKey key) {
        return jpaRepo.findByIdempotencyKey(key.value()).map(TransferMapper::toDomain);
    }

    @Override
    public Transfer save(Transfer transfer) {
        var entity = TransferMapper.toEntity(transfer);
        var saved = jpaRepo.save(entity);
        return TransferMapper.toDomain(saved);
    }
}

