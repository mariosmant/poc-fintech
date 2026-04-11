package com.mariosmant.fintech.domain.port.outbound;

import com.mariosmant.fintech.domain.event.DomainEvent;

/**
 * Outbound port for publishing domain events.
 *
 * <p>In a Transactional Outbox pattern, the implementation writes events
 * to an outbox table within the same DB transaction, then a poller
 * publishes them to Kafka asynchronously.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public interface EventPublisher {

    /**
     * Publishes a domain event.
     *
     * @param event the event to publish
     */
    void publish(DomainEvent event);
}

