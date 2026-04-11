package com.mariosmant.fintech.domain.model;

import com.mariosmant.fintech.domain.event.DomainEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract base class for all DDD Aggregate Roots.
 *
 * <p>Collects domain events that occurred during the current business operation.
 * Events are harvested by the application layer after persisting the aggregate
 * and then published via the Transactional Outbox pattern.</p>
 *
 * <p><b>Optimistic locking:</b> Concrete aggregates hold a {@code version} field
 * mapped to a JPA {@code @Version} column — no pessimistic locks are used.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public abstract class AggregateRoot {

    /** Transient list — not persisted; cleared after each unit of work. */
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    /**
     * Registers a domain event to be published after the aggregate is saved.
     *
     * @param event the domain event; must not be {@code null}
     */
    protected void registerEvent(DomainEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Domain event must not be null");
        }
        domainEvents.add(event);
    }

    /**
     * Returns an unmodifiable snapshot of the currently registered events.
     *
     * @return list of pending domain events
     */
    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /**
     * Clears all registered events. Called after events have been
     * persisted to the outbox.
     */
    public void clearEvents() {
        domainEvents.clear();
    }
}

