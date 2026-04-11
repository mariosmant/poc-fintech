package com.mariosmant.fintech.application.port;

import com.mariosmant.fintech.application.outbox.OutboxEvent;

import java.util.List;
import java.util.UUID;

/**
 * Port for the Transactional Outbox persistence.
 *
 * @author mariosmant
 * @since 1.0.0
 */
public interface OutboxRepository {

    OutboxEvent save(OutboxEvent event);

    /**
     * Finds unpublished events ordered by creation time (ensures correct ordering).
     *
     * @param batchSize maximum number of events to return
     * @return ordered list of unpublished events
     */
    List<OutboxEvent> findUnpublished(int batchSize);

    /**
     * Marks an event as published.
     *
     * @param eventId the outbox event ID
     */
    void markPublished(UUID eventId);
}

