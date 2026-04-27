package com.mariosmant.fintech.infrastructure.security.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes new audit rows <b>atomically with chain advancement</b>.
 *
 * <h2>Concurrency model</h2>
 * <p>Two concurrent audited actions can both try to insert into
 * {@code audit_log} at the same instant. Without coordination they would
 * both read the same {@code last_hash} / {@code last_seq} and produce a
 * <b>fork</b> in the chain — two rows with different {@code prev_hash}
 * but the same {@code chain_seq}, breaking subsequent verification.</p>
 *
 * <p>We serialise writers on the single row of {@code audit_log_head}
 * via {@code SELECT ... FOR UPDATE}:</p>
 * <ol>
 *   <li>Acquire a row-level exclusive lock on {@code audit_log_head}.</li>
 *   <li>Compute {@code chain_seq = last_seq + 1} and {@code prev_hash = last_hash}.</li>
 *   <li>Compute {@code row_hash = HMAC(key, prev_hash || chain_seq || canonical(row))}.</li>
 *   <li>INSERT into {@code audit_log}.</li>
 *   <li>UPDATE {@code audit_log_head} with the new head.</li>
 *   <li>COMMIT. The row lock is released; the next writer proceeds.</li>
 * </ol>
 *
 * <p>Under heavy audit-write contention this single lock becomes a throughput
 * cap — expected and accepted for audit logs, which are high-integrity rather
 * than high-throughput. A real deployment may shard by {@code chain_id}
 * (tenant-per-chain) to raise the ceiling; the head table schema is already
 * keyed for it.</p>
 *
 * <h2>Transaction semantics</h2>
 * <p>Audit is a <b>best-effort</b> cross-cut: a failure here must not roll
 * back the business method. We run this write in a <b>REQUIRES_NEW</b>
 * transaction so a failure stays isolated — the caller's transaction commits
 * (or rolls back) on its own merits. This is the industry consensus for audit
 * aspects (OWASP ASVS V7.1.1).</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Component
public class AuditChainWriter {

    private static final String CHAIN_ID = "default";

    private final JdbcTemplate jdbc;
    private final AuditChainHasher hasher;

    public AuditChainWriter(JdbcTemplate jdbc, AuditChainHasher hasher) {
        this.jdbc = jdbc;
        this.hasher = hasher;
    }

    /**
     * Insert one audit entry into the HMAC chain.
     *
     * <p>Executed in its own transaction so audit-write failures never
     * corrupt the calling business transaction.</p>
     *
     * @param entity the audit entity; its {@code id}, user/resource fields,
     *               and {@code createdAt} must already be set.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(AuditLogEntity entity) {
        // Ensure createdAt is pinned BEFORE we canonicalise — the JPA
        // @CreatedDate listener does not fire via this JdbcTemplate path,
        // and the verifier must see the identical value that was persisted.
        if (entity.getCreatedAt() == null) {
            setCreatedAtReflectively(entity, java.time.Instant.now());
        }

        // 1. Lock the chain head (single row of audit_log_head). Also
        //    reads `active_key_id` so the writer signs under the currently-
        //    active key (rotation: bump the column, restart picks it up).
        Head head = jdbc.queryForObject(
                "SELECT last_seq, last_hash, active_key_id FROM audit_log_head "
                        + "WHERE chain_id = ? FOR UPDATE",
                (rs, i) -> new Head(
                        rs.getLong("last_seq"),
                        rs.getBytes("last_hash"),
                        rs.getInt("active_key_id")),
                CHAIN_ID
        );
        if (head == null) {
            throw new IllegalStateException("audit_log_head row missing for chain_id=" + CHAIN_ID);
        }

        // The DB-recorded active id wins over the in-memory hasher default —
        // operators can flip rotation without redeploy. The hasher will refuse
        // (UnknownKeyException) if the chosen id isn't in the keyring.
        int keyId = head.activeKeyId();
        long nextSeq = head.lastSeq() + 1L;
        byte[] rowHash = hasher.computeRowHash(keyId, head.lastHash(), nextSeq, entity);

        // 2. Insert the audit row with its chain values + key_id.
        jdbc.update(
                "INSERT INTO audit_log ("
                        + "id, user_id, username, action, resource_type, resource_id, details, "
                        + "ip_address, http_method, request_uri, response_status, duration_ms, "
                        + "created_at, prev_hash, row_hash, chain_seq, key_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                entity.getId(),
                entity.getUserId(),
                entity.getUsername(),
                entity.getAction(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getDetails(),
                entity.getIpAddress(),
                entity.getHttpMethod(),
                entity.getRequestUri(),
                entity.getResponseStatus(),
                entity.getDurationMs(),
                java.sql.Timestamp.from(entity.getCreatedAt()),
                head.lastHash(),
                rowHash,
                nextSeq,
                keyId
        );

        // 3. Advance the head atomically in the same transaction.
        jdbc.update(
                "UPDATE audit_log_head SET last_seq = ?, last_hash = ?, updated_at = NOW() "
                        + "WHERE chain_id = ?",
                nextSeq, rowHash, CHAIN_ID
        );
    }

    private static void setCreatedAtReflectively(AuditLogEntity entity, java.time.Instant when) {
        // Truncate to microseconds — Postgres TIMESTAMPTZ stores µs; any
        // nanos would be lost on write and break the HMAC on read-back.
        java.time.Instant truncated = when.with(
                java.time.temporal.ChronoField.NANO_OF_SECOND,
                (when.getNano() / 1_000) * 1_000);
        try {
            java.lang.reflect.Field f = AuditLogEntity.class.getDeclaredField("createdAt");
            f.setAccessible(true);
            f.set(entity, truncated);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to pin audit createdAt", ex);
        }
    }

    private record Head(long lastSeq, byte[] lastHash, int activeKeyId) {
    }
}
