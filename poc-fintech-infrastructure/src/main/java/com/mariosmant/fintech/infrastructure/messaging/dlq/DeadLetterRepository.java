package com.mariosmant.fintech.infrastructure.messaging.dlq;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for dead letter queue entries.
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Repository
public interface DeadLetterRepository extends JpaRepository<DeadLetterEntity, UUID> {
}

