package com.mariosmant.fintech.infrastructure.security.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Replays the HMAC chain across all rows in {@code audit_log} and reports
 * the first point at which the chain diverges from what the HMAC key expects.
 *
 * <h2>What it catches</h2>
 * <ul>
 *   <li><b>Row modification</b> — any field change breaks {@code row_hash}.</li>
 *   <li><b>Row deletion</b> — the successor's {@code prev_hash} no longer
 *       matches the predecessor's recomputed {@code row_hash}.</li>
 *   <li><b>Row insertion</b> — the fabricated row's hash either doesn't
 *       match (attacker lacks the HMAC key) or its successor breaks.</li>
 *   <li><b>Row reordering</b> — {@code chain_seq} is bound into the HMAC
 *       input, so swapping two rows' order breaks the subsequent link.</li>
 *   <li><b>Truncation</b> — the last verified {@code row_hash} is compared
 *       against {@code audit_log_head.last_hash}; a mismatch signals that
 *       rows past the reported head were dropped.</li>
 * </ul>
 *
 * <h2>What it cannot catch (threat-model caveat)</h2>
 * <p>An attacker who obtains the HMAC key <i>and</i> write access to both
 * {@code audit_log} and {@code audit_log_head} can forge a valid chain.
 * That is why the key lives outside the database (KMS / env var) — defence
 * in depth with the V9 immutability trigger plus the V10 chain makes a
 * successful silent tamper require compromise of three independent
 * surfaces (DB writer role, trigger drop privilege, HMAC key).</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Component
public class AuditChainVerifier {

    private static final Logger log = LoggerFactory.getLogger(AuditChainVerifier.class);
    private static final int BATCH_SIZE = 1_000;
    private static final byte[] GENESIS = new byte[32]; // all-zeros anchor

    private final JdbcTemplate jdbc;
    private final AuditChainHasher hasher;

    public AuditChainVerifier(JdbcTemplate jdbc, AuditChainHasher hasher) {
        this.jdbc = jdbc;
        this.hasher = hasher;
    }

    /**
     * Walk the full chain.
     *
     * @return a {@link Report} with {@code valid=true} on success, or the
     *         first offending {@code chain_seq} and a diagnostic message.
     */
    public Report verifyAll() {
        byte[] expectedPrev = GENESIS;
        long expectedSeq = 0;
        long scanned = 0;
        long lastSeen = 0;
        Instant started = Instant.now();

        while (true) {
            List<Row> page = jdbc.query(
                    "SELECT id, user_id, username, action, resource_type, resource_id, details, "
                            + "ip_address, http_method, request_uri, response_status, duration_ms, "
                            + "created_at, prev_hash, row_hash, chain_seq, key_id "
                            + "FROM audit_log WHERE chain_seq > ? ORDER BY chain_seq ASC LIMIT ?",
                    (rs, i) -> new Row(
                            rs.getObject("id", UUID.class),
                            rs.getString("user_id"),
                            rs.getString("username"),
                            rs.getString("action"),
                            rs.getString("resource_type"),
                            rs.getString("resource_id"),
                            rs.getString("details"),
                            rs.getString("ip_address"),
                            rs.getString("http_method"),
                            rs.getString("request_uri"),
                            (Integer) rs.getObject("response_status"),
                            (Long) rs.getObject("duration_ms"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getBytes("prev_hash"),
                            rs.getBytes("row_hash"),
                            rs.getLong("chain_seq"),
                            rs.getInt("key_id")
                    ),
                    lastSeen, BATCH_SIZE);
            if (page.isEmpty()) break;

            for (Row r : page) {
                expectedSeq++;

                if (r.chainSeq() != expectedSeq) {
                    return Report.invalid(r.chainSeq(),
                            "chain_seq gap/duplicate: expected " + expectedSeq
                                    + " got " + r.chainSeq());
                }
                if (!Arrays.equals(expectedPrev, r.prevHash())) {
                    return Report.invalid(r.chainSeq(),
                            "prev_hash mismatch at seq " + r.chainSeq()
                                    + " (expected " + hex(expectedPrev) + " got " + hex(r.prevHash()) + ")");
                }

                AuditLogEntity e = r.toEntity();
                // re-sign under the row's recorded key_id, not under
                // the writer's currently-active key — so verification still
                // works after a key rotation.
                byte[] recomputed;
                try {
                    recomputed = hasher.computeRowHash(r.keyId(), expectedPrev, r.chainSeq(), e);
                } catch (AuditChainKeyRing.UnknownKeyException uke) {
                    return Report.invalid(r.chainSeq(),
                            "unknown key_id=" + r.keyId() + " at seq " + r.chainSeq()
                                    + " — keyring missing a historical key");
                }
                if (!Arrays.equals(recomputed, r.rowHash())) {
                    return Report.invalid(r.chainSeq(),
                            "row_hash mismatch at seq " + r.chainSeq()
                                    + " — entity contents tampered");
                }

                expectedPrev = r.rowHash();
                lastSeen = r.chainSeq();
                scanned++;
            }
            if (page.size() < BATCH_SIZE) break;
        }

        // Cross-check head
        byte[] headHash = jdbc.queryForObject(
                "SELECT last_hash FROM audit_log_head WHERE chain_id = 'default'", byte[].class);
        if (scanned > 0 && !Arrays.equals(expectedPrev, headHash)) {
            return Report.invalid(lastSeen,
                    "head divergence: rows past seq " + lastSeen + " were truncated");
        }

        long elapsedMs = java.time.Duration.between(started, Instant.now()).toMillis();
        log.info("Audit chain verified: {} rows in {} ms", scanned, elapsedMs);
        return Report.valid(scanned, elapsedMs);
    }

    private static String hex(byte[] b) {
        return b == null ? "null" : HexFormat.of().formatHex(b);
    }

    /** Verification result. */
    public record Report(boolean valid, long rowsVerified, long elapsedMs,
                         Long firstBadSeq, String message) {
        static Report valid(long rows, long ms) {
            return new Report(true, rows, ms, null, "OK");
        }

        static Report invalid(long seq, String msg) {
            return new Report(false, 0, 0, seq, msg);
        }
    }

    private record Row(UUID id, String userId, String username, String action,
                       String resourceType, String resourceId, String details,
                       String ipAddress, String httpMethod, String requestUri,
                       Integer responseStatus, Long durationMs, Instant createdAt,
                       byte[] prevHash, byte[] rowHash, long chainSeq, int keyId) {

        AuditLogEntity toEntity() {
            AuditLogEntity e = new AuditLogEntity(
                    id, userId, username, action, resourceType, resourceId, details,
                    ipAddress, httpMethod, requestUri, responseStatus, durationMs);
            // createdAt is @CreatedDate-driven on insert; we reflect it here so
            // the canonicaliser sees exactly the timestamp that was stored.
            try {
                java.lang.reflect.Field f = AuditLogEntity.class.getDeclaredField("createdAt");
                f.setAccessible(true);
                f.set(e, createdAt);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
            return e;
        }
    }
}



