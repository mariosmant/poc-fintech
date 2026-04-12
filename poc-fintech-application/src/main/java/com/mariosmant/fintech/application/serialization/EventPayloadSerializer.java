package com.mariosmant.fintech.application.serialization;

import com.mariosmant.fintech.domain.event.*;

/**
 * Serializes domain events to a stable JSON envelope for outbox/Kafka transport.
 */
public final class EventPayloadSerializer {

    private EventPayloadSerializer() {
        // utility class
    }

    public static String toJson(DomainEvent event) {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        writeString(sb, "eventType", event.getClass().getSimpleName());
        sb.append(',');
        writeString(sb, "eventId", event.eventId().toString());
        sb.append(',');
        writeString(sb, "aggregateId", event.aggregateId());
        sb.append(',');
        writeString(sb, "occurredAt", event.occurredAt().toString());

        switch (event) {
            case TransferInitiatedEvent e -> {
                sb.append(',');
                writeString(sb, "transferId", e.transferId().value().toString());
                sb.append(',');
                writeString(sb, "sourceAccountId", e.sourceAccountId().value().toString());
                sb.append(',');
                writeString(sb, "targetAccountId", e.targetAccountId().value().toString());
                sb.append(',');
                writeNumber(sb, "amount", e.amount().toPlainString());
                sb.append(',');
                writeString(sb, "sourceCurrency", e.sourceCurrency().name());
                sb.append(',');
                writeString(sb, "targetCurrency", e.targetCurrency().name());
                sb.append(',');
                writeString(sb, "idempotencyKey", e.idempotencyKey());
            }
            case FraudCheckCompletedEvent e -> {
                sb.append(',');
                writeString(sb, "transferId", e.transferId().value().toString());
                sb.append(',');
                writeBoolean(sb, "approved", e.approved());
                sb.append(',');
                writeString(sb, "reason", e.reason());
                sb.append(',');
                writeNumber(sb, "riskScore", Integer.toString(e.riskScore()));
            }
            case FxConversionCompletedEvent e -> {
                sb.append(',');
                writeString(sb, "transferId", e.transferId().value().toString());
                sb.append(',');
                writeNumber(sb, "exchangeRate", e.exchangeRate().toPlainString());
                sb.append(',');
                writeNumber(sb, "convertedAmount", e.convertedAmount().toPlainString());
                sb.append(',');
                writeString(sb, "targetCurrency", e.targetCurrency().name());
            }
            case AccountDebitedEvent e -> {
                sb.append(',');
                writeString(sb, "accountId", e.accountId().value().toString());
                sb.append(',');
                writeString(sb, "transferId", e.transferId().value().toString());
                sb.append(',');
                writeNumber(sb, "amount", e.amount().toPlainString());
            }
            case AccountCreditedEvent e -> {
                sb.append(',');
                writeString(sb, "accountId", e.accountId().value().toString());
                sb.append(',');
                writeString(sb, "transferId", e.transferId().value().toString());
                sb.append(',');
                writeNumber(sb, "amount", e.amount().toPlainString());
            }
            case LedgerEntryRecordedEvent e -> {
                sb.append(',');
                writeString(sb, "ledgerEntryId", e.ledgerEntryId().value().toString());
                sb.append(',');
                writeString(sb, "debitAccountId", e.debitAccountId().value().toString());
                sb.append(',');
                writeString(sb, "creditAccountId", e.creditAccountId().value().toString());
                sb.append(',');
                writeNumber(sb, "amount", e.amount().toPlainString());
                sb.append(',');
                writeString(sb, "transferId", e.transferId().value().toString());
            }
            case TransferCompletedEvent e -> {
                sb.append(',');
                writeString(sb, "transferId", e.transferId().value().toString());
            }
            case TransferFailedEvent e -> {
                sb.append(',');
                writeString(sb, "transferId", e.transferId().value().toString());
                sb.append(',');
                writeString(sb, "reason", e.reason());
            }
        }

        sb.append('}');
        return sb.toString();
    }

    private static void writeString(StringBuilder sb, String key, String value) {
        sb.append('"').append(escape(key)).append('"').append(':')
                .append('"').append(escape(value)).append('"');
    }

    private static void writeNumber(StringBuilder sb, String key, String value) {
        sb.append('"').append(escape(key)).append('"').append(':').append(value);
    }

    private static void writeBoolean(StringBuilder sb, String key, boolean value) {
        sb.append('"').append(escape(key)).append('"').append(':').append(value);
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}

