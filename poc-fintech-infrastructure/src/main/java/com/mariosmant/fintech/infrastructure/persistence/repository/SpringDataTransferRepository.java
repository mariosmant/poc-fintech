package com.mariosmant.fintech.infrastructure.persistence.repository;

import com.mariosmant.fintech.infrastructure.persistence.entity.TransferJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link TransferJpaEntity}.
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Repository
public interface SpringDataTransferRepository extends JpaRepository<TransferJpaEntity, UUID> {

    /** Finds a transfer by idempotency key for duplicate detection. */
    Optional<TransferJpaEntity> findByIdempotencyKey(String idempotencyKey);
}

