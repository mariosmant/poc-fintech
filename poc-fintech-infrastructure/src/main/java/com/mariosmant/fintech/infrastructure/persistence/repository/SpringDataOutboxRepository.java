package com.mariosmant.fintech.infrastructure.persistence.repository;

import com.mariosmant.fintech.infrastructure.persistence.entity.OutboxEventJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for the Transactional Outbox table.
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Repository
public interface SpringDataOutboxRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    /**
     * Finds unpublished events ordered by creation time (correct ordering guarantee).
     *
     * @param pageable paging specification (limits batch size)
     * @return list of unpublished outbox events, oldest first
     */
    @Query("SELECT e FROM OutboxEventJpaEntity e WHERE e.published = false ORDER BY e.createdAt ASC")
    List<OutboxEventJpaEntity> findUnpublished(Pageable pageable);

    /**
     * Marks an outbox event as published so it won't be re-sent.
     *
     * @param eventId the outbox event ID
     */
    @Modifying
    @Query("UPDATE OutboxEventJpaEntity e SET e.published = true WHERE e.id = :eventId")
    void markPublished(UUID eventId);
}

