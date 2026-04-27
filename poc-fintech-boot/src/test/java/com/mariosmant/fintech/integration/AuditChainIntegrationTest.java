package com.mariosmant.fintech.integration;

import com.mariosmant.fintech.infrastructure.security.audit.AuditChainVerifier;
import com.mariosmant.fintech.infrastructure.security.audit.AuditChainWriter;
import com.mariosmant.fintech.infrastructure.security.audit.AuditLogEntity;
import com.mariosmant.fintech.testcontainers.EnabledIfDockerAvailable;
import com.mariosmant.fintech.testcontainers.TestSecurityConfig;
import com.mariosmant.fintech.testcontainers.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the tamper-evident audit chain.
 *
 * <p>Boots a full Spring context with a real PostgreSQL container so all
 * migrations (including V9 immutability triggers and V10 chain columns) are
 * applied. Exercises:</p>
 * <ul>
 *   <li>Appending rows through {@link AuditChainWriter} produces a verifiable
 *       chain.</li>
 *   <li>{@link AuditChainVerifier} returns {@code valid=true} for an
 *       un-tampered chain.</li>
 *   <li>When a privileged path (simulated here by disabling triggers as
 *       superuser) mutates a row, the verifier detects the divergence and
 *       reports the offending {@code chain_seq}.</li>
 * </ul>
 */
@EnabledIfDockerAvailable
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfig.class, TestSecurityConfig.class})
@ActiveProfiles("test")
class AuditChainIntegrationTest {

    @Autowired
    AuditChainWriter writer;

    @Autowired
    AuditChainVerifier verifier;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("Empty chain verifies as valid (no rows, head anchored at zero)")
    void emptyChainIsValid() {
        resetChain();
        AuditChainVerifier.Report report = verifier.verifyAll();
        assertThat(report.valid()).isTrue();
        assertThat(report.rowsVerified()).isZero();
    }

    @Test
    @DisplayName("Three appended rows verify as a valid chain")
    void appendedRowsVerify() {
        resetChain();
        for (int i = 0; i < 3; i++) {
            writer.append(sample("action-" + i));
        }
        AuditChainVerifier.Report report = verifier.verifyAll();
        assertThat(report.valid()).as(report.message()).isTrue();
        assertThat(report.rowsVerified()).isEqualTo(3);
    }

    @Test
    @DisplayName("Silent tamper of an audit row is detected by the verifier")
    void tamperIsDetected() {
        resetChain();
        writer.append(sample("alpha"));
        writer.append(sample("beta"));
        writer.append(sample("gamma"));

        // Act as an attacker who dropped the V9 trigger: disable session
        // replication role to bypass BEFORE UPDATE triggers in this session
        // only. (PostgreSQL lets a superuser-equivalent do this; the Testcontainers
        // default role is such.)
        jdbc.execute("SET session_replication_role = 'replica'");
        try {
            int rows = jdbc.update(
                    "UPDATE audit_log SET action = 'FORGED' WHERE chain_seq = 2");
            assertThat(rows).isEqualTo(1);
        } finally {
            jdbc.execute("SET session_replication_role = 'origin'");
        }

        AuditChainVerifier.Report report = verifier.verifyAll();
        assertThat(report.valid()).isFalse();
        assertThat(report.firstBadSeq()).isEqualTo(2L);
        assertThat(report.message()).contains("row_hash mismatch");
    }

    @Test
    @DisplayName("Truncation past the head is detected via audit_log_head cross-check")
    void truncationIsDetected() {
        resetChain();
        writer.append(sample("one"));
        writer.append(sample("two"));
        writer.append(sample("three"));

        // Silently drop the last row (bypassing V9 trigger).
        jdbc.execute("SET session_replication_role = 'replica'");
        try {
            jdbc.update("DELETE FROM audit_log WHERE chain_seq = 3");
        } finally {
            jdbc.execute("SET session_replication_role = 'origin'");
        }

        AuditChainVerifier.Report report = verifier.verifyAll();
        assertThat(report.valid()).isFalse();
        assertThat(report.message()).contains("head divergence");
    }

    @Test
    @DisplayName("every appended row carries key_id and audit_log_head exposes active_key_id")
    void rowsCarryKeyId() {
        resetChain();
        writer.append(sample("rotated"));

        Integer rowKeyId = jdbc.queryForObject(
                "SELECT key_id FROM audit_log WHERE chain_seq = 1", Integer.class);
        Integer headActive = jdbc.queryForObject(
                "SELECT active_key_id FROM audit_log_head WHERE chain_id = 'default'", Integer.class);
        assertThat(rowKeyId).as("V11 backfill: every row records its signing key id")
                .isEqualTo(1);
        assertThat(headActive).as("V11 backfill: head has a default active key id")
                .isEqualTo(1);

        // Verifier still passes — single key, id=1 — so rotation column is
        // additive and back-compatible with the chain.
        AuditChainVerifier.Report report = verifier.verifyAll();
        assertThat(report.valid()).as(report.message()).isTrue();
    }

    // ── helpers ─────────────────────────────────────────────────

    private AuditLogEntity sample(String action) {
        return new AuditLogEntity(
                UUID.randomUUID(),
                "user-xyz", "alice", action, "Transfer", "txn-" + action,
                null, "127.0.0.1", "POST", "/api/v1/transfers", 201, 10L);
    }

    /**
     * Wipe the chain between tests. Uses session_replication_role = 'replica'
     * to bypass the V9 immutability triggers — safe in a disposable test
     * container, forbidden in any real deployment.
     */
    private void resetChain() {
        jdbc.execute("SET session_replication_role = 'replica'");
        try {
            jdbc.update("DELETE FROM audit_log");
            jdbc.update("UPDATE audit_log_head SET last_seq = 0, "
                    + "last_hash = decode(repeat('00',32),'hex'), updated_at = NOW() "
                    + "WHERE chain_id = 'default'");
        } finally {
            jdbc.execute("SET session_replication_role = 'origin'");
        }
    }
}


