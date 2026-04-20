package com.mariosmant.fintech.infrastructure.messaging.consumer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.mariosmant.fintech.application.saga.TransferSagaOrchestrator;
import com.mariosmant.fintech.domain.event.*;
import com.mariosmant.fintech.domain.model.vo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class TransferSagaEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(TransferSagaEventConsumer.class);
    private final TransferSagaOrchestrator sagaOrchestrator;
    private final ObjectMapper objectMapper;

    public TransferSagaEventConsumer(TransferSagaOrchestrator sagaOrchestrator, ObjectMapper objectMapper) {
        this.sagaOrchestrator = sagaOrchestrator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "transfer-events", groupId = "transfer-saga-group")
    public void onTransferEvent(String message, Acknowledgment acknowledgment) {
        // Exceptions propagate to DefaultErrorHandler for retry + DLQ routing.
        // Do NOT catch and swallow — let the error handler manage retries.
        try {
            JsonNode json = objectMapper.readTree(message);
            String eventType = json.get("eventType").asText();
            DomainEvent event = parseEvent(json, eventType);
            if (event != null) {
                sagaOrchestrator.handle(event);
            } else {
                log.warn("Ignoring unsupported event type: {}", eventType);
            }
            acknowledgment.acknowledge();
        } catch (Exception ex) {
            log.error("Kafka message processing error — will be retried or sent to DLT", ex);
            throw new RuntimeException("Failed to process transfer event", ex);
        }
    }

    private DomainEvent parseEvent(JsonNode json, String type) throws Exception {
        UUID eventId = json.hasNonNull("eventId")
                ? UUID.fromString(json.get("eventId").asText())
                : UUID.randomUUID();
        String agg = json.get("aggregateId").asText();
        Instant ts = Instant.parse(json.get("occurredAt").asText());
        TransferId tid = new TransferId(UUID.fromString(json.get("transferId").asText()));

        return switch (type) {
            case "TransferInitiatedEvent" -> new TransferInitiatedEvent(
                eventId, agg, ts, tid,
                new AccountId(UUID.fromString(json.get("sourceAccountId").asText())),
                new AccountId(UUID.fromString(json.get("targetAccountId").asText())),
                new BigDecimal(json.get("amount").asText()),
                Currency.valueOf(json.get("sourceCurrency").asText()),
                Currency.valueOf(json.get("targetCurrency").asText()),
                json.get("idempotencyKey").asText()
            );
            case "FraudCheckCompletedEvent" -> new FraudCheckCompletedEvent(
                eventId, agg, ts, tid,
                json.get("approved").asBoolean(),
                json.get("reason").asText(),
                json.get("riskScore").asInt()
            );
            case "FxConversionCompletedEvent" -> new FxConversionCompletedEvent(
                eventId, agg, ts, tid,
                new BigDecimal(json.get("exchangeRate").asText()),
                new BigDecimal(json.get("convertedAmount").asText()),
                Currency.valueOf(json.get("targetCurrency").asText())
            );
            case "AccountDebitedEvent" -> new AccountDebitedEvent(
                eventId, agg, ts,
                new AccountId(UUID.fromString(json.get("accountId").asText())),
                tid,
                new BigDecimal(json.get("amount").asText())
            );
            case "AccountCreditedEvent" -> new AccountCreditedEvent(
                eventId, agg, ts,
                new AccountId(UUID.fromString(json.get("accountId").asText())),
                tid,
                new BigDecimal(json.get("amount").asText())
            );
            case "LedgerEntryRecordedEvent" -> new LedgerEntryRecordedEvent(
                eventId, agg, ts,
                new LedgerEntryId(UUID.fromString(json.get("ledgerEntryId").asText())),
                new AccountId(UUID.fromString(json.get("debitAccountId").asText())),
                new AccountId(UUID.fromString(json.get("creditAccountId").asText())),
                new BigDecimal(json.get("amount").asText()),
                tid
            );
            default -> null;
        };
    }
}
