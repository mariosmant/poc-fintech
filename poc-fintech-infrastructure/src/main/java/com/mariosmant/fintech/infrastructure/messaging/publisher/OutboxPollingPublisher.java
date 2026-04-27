package com.mariosmant.fintech.infrastructure.messaging.publisher;

import com.mariosmant.fintech.application.outbox.OutboxEvent;
import com.mariosmant.fintech.application.port.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * {@code OutboxPollingPublisher} — relays events from the {@code outbox_events}
 * table to Kafka, implementing the <b>Transactional Outbox</b> pattern
 * (Richardson — Microservices Patterns §3.3; Fowler — "Dual Writes" anti-pattern).
 *
 * <h2>Why a Transactional Outbox?</h2>
 * <p>A naïve implementation would {@code save()} the domain state and then
 * {@code kafkaTemplate.send()} the event. Without XA, that is a <b>dual write</b>:
 * the DB commit and the Kafka publish can succeed or fail independently. Typical
 * failure modes:</p>
 * <ul>
 *   <li>DB commit succeeds, Kafka publish times out → lost event, ledger and
 *       downstream consumers diverge.</li>
 *   <li>DB commit rolls back after Kafka publish → phantom event; consumers
 *       react to something that never happened.</li>
 * </ul>
 * <p>The outbox moves the event into the <i>same local transaction</i> as the
 * state change. The publisher then tails the outbox asynchronously and
 * publishes at-least-once. Combined with an idempotent consumer this yields
 * <b>effectively-once</b> end-to-end delivery without distributed
 * transactions.</p>
 *
 * <h2>Mechanism</h2>
 * <ol>
 *   <li>Polls the outbox every 500 ms (tunable) for unpublished rows, ordered
 *       by {@code created_at} so aggregate-level ordering is preserved.</li>
 *   <li>For each row, publishes to the {@code transfer-events} Kafka topic
 *       using {@code aggregateId} as the <b>partition key</b>. This guarantees
 *       that all events for the same Transfer land on the same partition, so
 *       the consumer sees them in source order regardless of partition count
 *       (Kafka ordering is per-partition only).</li>
 *   <li>On ack-from-broker, the row is marked {@code published_at = NOW()}
 *       inside a short transaction. A crash between "publish" and "mark
 *       published" causes a duplicate on restart, which the consumer discards
 *       via idempotency key — no data loss, no double processing.</li>
 * </ol>
 *
 * <h2>Operational notes</h2>
 * <ul>
 *   <li><b>Backpressure:</b> batch size is capped ({@value #BATCH_SIZE}) so a
 *       backlog cannot exhaust connections or memory.</li>
 *   <li><b>Concurrency:</b> the JPA adapter uses {@code SELECT … FOR UPDATE
 *       SKIP LOCKED} (Postgres native), so multiple boot instances can poll
 *       the same outbox in parallel without double-publishing — each instance
 *       sees only rows that aren't currently row-locked by another poller.</li>
 *   <li><b>DLQ:</b> the paired consumer routes un-deserialisable payloads to
 *       {@code transfer-events.DLT} → {@code dead_letter_queue} for manual
 *       replay — see {@code DeadLetterQueueConsumer} and migration V7.</li>
 * </ul>
 *
 * <h2>Standards touched</h2>
 * <ul>
 *   <li><b>NIST SP 800-53 CP-10</b> — information system recovery &amp;
 *       reconstitution (we can rebuild downstream state by re-publishing
 *       the outbox).</li>
 *   <li><b>PCI DSS §10.2.1</b> — all user-initiated actions produce events
 *       that survive infrastructure failures and are still available for
 *       audit reconstruction.</li>
 * </ul>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Component
public class OutboxPollingPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPollingPublisher.class);
    private static final String TOPIC = "transfer-events";
    private static final int BATCH_SIZE = 100;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPollingPublisher(OutboxRepository outboxRepository,
                                  KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Scheduled polling — runs every 500ms.
     * Publishes unpublished outbox events to Kafka.
     */
    @Scheduled(fixedDelay = 500)
    @Transactional
    public void pollAndPublish() {
        List<OutboxEvent> events = outboxRepository.findUnpublished(BATCH_SIZE);
        if (events.isEmpty()) {
            return;
        }

        for (OutboxEvent event : events) {
            try {
                // Use aggregateId as Kafka key for partition-level ordering
                // Wait for broker acknowledgment before marking as published.
                kafkaTemplate.send(TOPIC, event.getAggregateId(), event.getPayload())
                        .get(10, TimeUnit.SECONDS);
                outboxRepository.markPublished(event.getId());
                log.debug("Published outbox event: id={}, type={}", event.getId(), event.getEventType());
            } catch (Exception ex) {
                log.error("Failed to publish outbox event: id={}", event.getId(), ex);
                // Stop processing this batch — will be retried on next poll
                break;
            }
        }
    }
}
