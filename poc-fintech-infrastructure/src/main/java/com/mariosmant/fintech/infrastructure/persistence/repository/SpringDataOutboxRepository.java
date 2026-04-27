package com.mariosmant.fintech.infrastructure.persistence.repository;

import com.mariosmant.fintech.infrastructure.persistence.entity.OutboxEventJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for the Transactional Outbox table.
 *
 * <h2>multi-instance safety via {@code SELECT … FOR UPDATE SKIP LOCKED}</h2>
 * <p>The polling publisher runs on every boot instance. Without coordination,
 * two instances would each pick up the same unpublished rows and attempt to
 * publish them — at-least-twice delivery in the duplicate-explosion sense, plus
 * a wasted Kafka round-trip per duplicate. PostgreSQL's
 * {@code FOR UPDATE SKIP LOCKED} (Postgres 9.5+) makes the publisher
 * <i>contention-free</i>: each instance sees only rows that aren't currently
 * locked by another transaction, so the work is naturally partitioned across
 * pollers without leader election or row-level checkpointing.</p>
 *
 * <p>Ordering caveat: per-aggregate ordering is preserved by the Kafka
 * partition key ({@code aggregateId}); cross-aggregate ordering is not
 * guaranteed and never was — see {@code OutboxPollingPublisher}.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Repository
public interface SpringDataOutboxRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    /**
     * Find unpublished events ordered by creation time, locking each returned
     * row exclusively and skipping rows already locked by other pollers.
     *
     * <p>Native SQL is required: JPQL has no first-class {@code SKIP LOCKED}
     * vocabulary, and the JPA {@code @Lock(PESSIMISTIC_WRITE)} hint maps to
     * {@code FOR UPDATE} (which would block, not skip). The native form is
     * Postgres-specific; H2 / MySQL substitute equivalents but production
     * targets PG 16+.</p>
     *
     * @param batchSize maximum number of events to return
     * @return list of unpublished events, oldest first, exclusively locked
     */
    @Query(value = """
            SELECT *
              FROM outbox_events
             WHERE published = false
             ORDER BY created_at ASC
             LIMIT :batchSize
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventJpaEntity> findUnpublishedSkipLocked(@Param("batchSize") int batchSize);

    /**
     * Marks an outbox event as published so it won't be re-sent.
     *
     * @param eventId the outbox event ID
     */
    @Modifying
    @Query("UPDATE OutboxEventJpaEntity e SET e.published = true WHERE e.id = :eventId")
    void markPublished(UUID eventId);

    /**
     * outbox shedding. Bulk-delete already-published
     * rows older than {@code cutoff} so the table doesn't grow unboundedly.
     *
     * <p>Only {@code published=true} rows are eligible — we never delete a row
     * that hasn't shipped to Kafka yet, even if it's old. The {@code aggregateId}
     * partition key on Kafka guarantees per-aggregate ordering, and the
     * idempotent consumer dedupes replays, so dropping shipped rows after a
     * retention window is safe.</p>
     *
     * @param cutoff oldest {@code created_at} to keep; everything older AND
     *               published is removed.
     * @return number of rows actually deleted (logged by {@code OutboxShedder}).
     */
    @Modifying
    @Query("DELETE FROM OutboxEventJpaEntity e "
            + "WHERE e.published = true AND e.createdAt < :cutoff")
    int deletePublishedOlderThan(@Param("cutoff") Instant cutoff);
}
