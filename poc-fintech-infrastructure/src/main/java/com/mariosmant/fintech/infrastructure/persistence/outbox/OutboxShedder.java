package com.mariosmant.fintech.infrastructure.persistence.outbox;

import com.mariosmant.fintech.infrastructure.persistence.repository.SpringDataOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * PTransactional Outbox shedder.
 *
 * <h2>Why shed?</h2>
 * <p>The {@code outbox_events} table grows monotonically: every domain event
 * inserts a row {@code UPDATE…SET published=true}.
 * Without a shedder, a healthy production system writes ≥ N rows per day
 * and never deletes any of them — the table eventually:</p>
 * <ul>
 *   <li>bloats the {@code idx_outbox_unpublished} B-tree (every poller scan
 *       traverses dead tuples until VACUUM rebuilds it);</li>
 *   <li>extends the recovery time after a logical replication switchover;</li>
 *   <li>increases backup size and PITR replay time linearly.</li>
 * </ul>
 *
 * <h2>What this does</h2>
 * <p>Every {@code app.outbox.shedding.interval} (default 1 h) it deletes
 * {@code published=true} rows older than
 * {@code app.outbox.shedding.retention} (default 7 d). Only published rows
 * are touched — an event that hasn't yet reached Kafka is preserved
 * regardless of age (unbounded retention for the unpublished tail is by
 * design — it surfaces stuck publishers via {@code outbox_events} growth
 * alerts).</p>
 *
 * <h2>Multi-pod safety</h2>
 * <p>The bulk-delete is a single {@code DELETE … WHERE published=true AND
 * created_at &lt; ?} statement: row-level locks on individual rows are
 * unnecessary because a deleted row's only readers were the publisher
 * (which already shipped it) and another shedder (which would just delete
 * it again — idempotent). Concurrent shedders contend at the page-lock
 * level only; throughput is dominated by I/O, not lock waits.</p>
 *
 * <h2>Standards mapping</h2>
 * <ul>
 *   <li>NIST SP 800-53 Rev 5 — SI-12 Information Management and Retention.</li>
 *   <li>PCI DSS v4.0.1 — §3.2.1 (data retention only as long as needed).</li>
 *   <li>GDPR Art. 5(1)(e) — storage-limitation principle.</li>
 *   <li>(Transactional Outbox).</li>
 * </ul>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(name = "app.outbox.shedding.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxShedder {

    private static final Logger log = LoggerFactory.getLogger(OutboxShedder.class);

    private final SpringDataOutboxRepository repository;
    private final Duration retention;

    public OutboxShedder(SpringDataOutboxRepository repository,
                         @Value("${app.outbox.shedding.retention:P7D}") Duration retention) {
        this.repository = repository;
        this.retention = retention;
        log.info("OutboxShedder enabled — retention={} (deletes published rows older than now-retention)",
                retention);
    }

    /**
     * Periodically delete published outbox rows older than {@link #retention}.
     *
     * <p>{@code @Transactional} keeps the bulk-delete in one DB round-trip
     * and one WAL record. Fixed delay (not fixed rate) so a slow run never
     * piles up.</p>
     */
    @Scheduled(
            fixedDelayString = "${app.outbox.shedding.interval-ms:3600000}",   // 1 h
            initialDelayString = "${app.outbox.shedding.initial-delay-ms:60000}" // 60 s
    )
    @Transactional
    public void shedPublishedRows() {
        Instant cutoff = Instant.now().minus(retention);
        long started = System.nanoTime();
        int deleted = repository.deletePublishedOlderThan(cutoff);
        if (deleted > 0) {
            log.info("OutboxShedder: deleted {} published rows older than {} ({} ms)",
                    deleted, cutoff, (System.nanoTime() - started) / 1_000_000);
        } else {
            log.debug("OutboxShedder: nothing to shed (cutoff={})", cutoff);
        }
    }
}

