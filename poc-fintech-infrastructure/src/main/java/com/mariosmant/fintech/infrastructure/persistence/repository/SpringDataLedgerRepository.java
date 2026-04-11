package com.mariosmant.fintech.infrastructure.persistence.repository;

import com.mariosmant.fintech.infrastructure.persistence.entity.LedgerEntryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link LedgerEntryJpaEntity}.
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Repository
public interface SpringDataLedgerRepository extends JpaRepository<LedgerEntryJpaEntity, UUID> {

    List<LedgerEntryJpaEntity> findByTransferId(UUID transferId);

    @Query("SELECT e FROM LedgerEntryJpaEntity e WHERE e.debitAccountId = :accountId OR e.creditAccountId = :accountId ORDER BY e.createdAt DESC")
    List<LedgerEntryJpaEntity> findByAccountId(UUID accountId);
}

