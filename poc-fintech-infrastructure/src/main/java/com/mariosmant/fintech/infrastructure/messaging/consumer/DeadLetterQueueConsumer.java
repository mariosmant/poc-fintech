package com.mariosmant.fintech.infrastructure.messaging.consumer;

import com.mariosmant.fintech.infrastructure.messaging.dlq.DeadLetterEntity;
import com.mariosmant.fintech.infrastructure.messaging.dlq.DeadLetterRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Dead Letter Queue consumer — listens on the {@code transfer-events.DLT} topic
 * and persists failed messages to the {@code dead_letter_queue} table for
 * manual inspection and replay.
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Service
public class DeadLetterQueueConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterQueueConsumer.class);

    private final DeadLetterRepository deadLetterRepository;

    public DeadLetterQueueConsumer(DeadLetterRepository deadLetterRepository) {
        this.deadLetterRepository = deadLetterRepository;
    }

    @KafkaListener(topics = "transfer-events.DLT", groupId = "dlq-consumer-group")
    public void onDeadLetter(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            log.error("DLQ message received: topic={}, partition={}, offset={}, key={}",
                    record.topic(), record.partition(), record.offset(), record.key());

            var entity = new DeadLetterEntity();
            entity.setId(UUID.randomUUID());
            entity.setTopic("transfer-events");
            entity.setPartitionNum(record.partition());
            entity.setOffsetNum(record.offset());
            entity.setKey(record.key());
            entity.setPayload(record.value());
            entity.setErrorMessage("Exhausted retries — moved to DLT");

            deadLetterRepository.save(entity);
            acknowledgment.acknowledge();

            log.info("DLQ message persisted to dead_letter_queue table for manual review");
        } catch (Exception ex) {
            log.error("CRITICAL: Failed to persist DLQ message", ex);
        }
    }
}

