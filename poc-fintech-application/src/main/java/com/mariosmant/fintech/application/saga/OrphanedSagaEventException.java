package com.mariosmant.fintech.application.saga;

import com.mariosmant.fintech.domain.model.vo.TransferId;

/**
 * Thrown by the saga orchestrator when a Kafka-driven event references a
 * {@code Transfer} aggregate that no longer exists in the write-store.
 *
 * <p><b>Treatment:</b> non-retryable. The Kafka {@code DefaultErrorHandler}
 * (see {@code KafkaConfig.kafkaErrorHandler}) routes any exception of this
 * type <i>immediately</i> to {@code transfer-events.DLT} (the dead-letter
 * topic) without exponential backoff. The DLT consumer then persists the
 * record to the {@code dead_letter_queue} table (Flyway V7) for forensic
 * analysis and manual replay.</p>
 *
 * <h3>Why fast-DLT and not silent skip?</h3>
 * <p>An orphaned event is <i>operationally suspicious</i>. It can be benign
 * (DB volume reset while Kafka retained the topic — the typical local-dev
 * case) or a symptom of a real bug (race condition, accidental {@code DELETE},
 * mis-configured isolation level, restored-from-incorrect-snapshot).
 * Silently dropping the message would:</p>
 * <ul>
 *   <li>Violate PCI DSS v4.0.1 §10.2 / §10.5 — every security-relevant event
 *       must be retained for forensic audit.</li>
 *   <li>Violate NIST SP 800-53 AU-9 / AU-11 — log retention covers
 *       operationally significant events, not just successful ones.</li>
 *   <li>Hide real bugs behind a {@code WARN} log line that nobody alerts on.</li>
 * </ul>
 * <p>Fast-DLT preserves the message bit-for-bit, surfaces an alertable
 * signal (DLT lag metric), and lets operators distinguish "100 orphaned
 * events overnight" (incident) from "1 orphaned event after volume reset"
 * (operator-acknowledged).</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public final class OrphanedSagaEventException extends RuntimeException {

    private final TransferId transferId;
    private final String eventType;

    public OrphanedSagaEventException(TransferId transferId, String eventType) {
        super("Orphaned saga event " + eventType + " for non-existent transfer " + transferId
                + " (DB reset, race condition, or accidental delete) — routing to DLT for audit");
        this.transferId = transferId;
        this.eventType = eventType;
    }

    public TransferId transferId() {
        return transferId;
    }

    public String eventType() {
        return eventType;
    }
}

