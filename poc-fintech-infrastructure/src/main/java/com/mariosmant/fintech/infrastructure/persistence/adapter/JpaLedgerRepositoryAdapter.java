package com.mariosmant.fintech.infrastructure.persistence.adapter;

import com.mariosmant.fintech.domain.model.LedgerEntry;
import com.mariosmant.fintech.domain.model.vo.AccountId;
import com.mariosmant.fintech.domain.model.vo.TransferId;
import com.mariosmant.fintech.domain.port.outbound.LedgerRepository;
import com.mariosmant.fintech.infrastructure.persistence.mapper.LedgerEntryMapper;
import com.mariosmant.fintech.infrastructure.persistence.repository.SpringDataLedgerRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * JPA adapter implementing the {@link LedgerRepository} outbound port.
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Component
@Transactional
public class JpaLedgerRepositoryAdapter implements LedgerRepository {

    private final SpringDataLedgerRepository jpaRepo;

    public JpaLedgerRepositoryAdapter(SpringDataLedgerRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    public LedgerEntry save(LedgerEntry entry) {
        var entity = LedgerEntryMapper.toEntity(entry);
        var saved = jpaRepo.save(entity);
        return LedgerEntryMapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerEntry> findByTransferId(TransferId transferId) {
        return jpaRepo.findByTransferId(transferId.value())
                .stream().map(LedgerEntryMapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerEntry> findByAccountId(AccountId accountId) {
        return jpaRepo.findByAccountId(accountId.value())
                .stream().map(LedgerEntryMapper::toDomain).toList();
    }
}

