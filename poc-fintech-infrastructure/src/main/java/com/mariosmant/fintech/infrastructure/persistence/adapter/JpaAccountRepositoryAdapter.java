package com.mariosmant.fintech.infrastructure.persistence.adapter;

import com.mariosmant.fintech.domain.model.Account;
import com.mariosmant.fintech.domain.model.vo.AccountId;
import com.mariosmant.fintech.domain.port.outbound.AccountRepository;
import com.mariosmant.fintech.infrastructure.persistence.mapper.AccountMapper;
import com.mariosmant.fintech.infrastructure.persistence.repository.SpringDataAccountRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * JPA adapter implementing the {@link AccountRepository} outbound port.
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Component
@Transactional
public class JpaAccountRepositoryAdapter implements AccountRepository {

    private final SpringDataAccountRepository jpaRepo;

    public JpaAccountRepositoryAdapter(SpringDataAccountRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Account> findById(AccountId id) {
        return jpaRepo.findById(id.value()).map(AccountMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Account> findByIban(String iban) {
        return jpaRepo.findByIban(iban).map(AccountMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Account> findAll() {
        return jpaRepo.findAllByOrderByCreatedAtDesc()
                .stream().map(AccountMapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Account> findByOwnerId(String ownerId) {
        return jpaRepo.findByOwnerIdOrderByCreatedAtDesc(ownerId)
                .stream().map(AccountMapper::toDomain).toList();
    }

    @Override
    public Account save(Account account) {
        var entity = AccountMapper.toEntity(account);
        // Preserve ownerId from existing entity if updating
        jpaRepo.findById(account.getId().value()).ifPresent(existing ->
                entity.setOwnerId(existing.getOwnerId()));
        var saved = jpaRepo.save(entity);
        return AccountMapper.toDomain(saved);
    }

    @Override
    public Account save(Account account, String ownerId) {
        var entity = AccountMapper.toEntity(account);
        entity.setOwnerId(ownerId);
        var saved = jpaRepo.save(entity);
        return AccountMapper.toDomain(saved);
    }
}

