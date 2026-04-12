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
 * Outbox Polling Publisher — polls unpublished events from the outbox table
 * and publishes them to Kafka.
 *
 * <p>This implements the <b>Transactional Outbox</b> pattern:
 * <ol>
 *   <li>Polls the outbox table for unpublished events (ordered by creation time).</li>
 *   <li>Publishes each event to the Kafka topic using the aggregate ID as partition key
 *       (ensuring correct ordering per aggregate).</li>
 *   <li>Marks each event as published in the same transaction.</li>
 * </ol>
 *
 * <p>Combined with an idempotent consumer, this achieves exactly-once semantics.</p>
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
