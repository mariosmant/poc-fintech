package com.mariosmant.fintech.infrastructure.security.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the tamper-evidence primitives: {@link AuditChainHasher} and
 * {@link AuditCanonicaliser}. These unit tests are fast and Docker-free —
 * they pin the cryptographic contract that the database-backed writer
 * relies on.
 */
class AuditChainHasherTest {

    private static final String TEST_KEY_B64 =
            Base64.getEncoder().encodeToString(new byte[]{
                    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
                    17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32});

    private AuditLogEntity sampleEntity() {
        return new AuditLogEntity(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "user-1", "alice", "CREATE_TRANSFER",
                "Transfer", "txn-1", null,
                "127.0.0.1", "POST", "/api/v1/transfers",
                201, 42L);
    }

    @Test
    @DisplayName("HMAC output is deterministic for identical inputs")
    void deterministic() {
        var h = new AuditChainHasher(TEST_KEY_B64);
        byte[] prev = new byte[32];
        var e = sampleEntity();
        // Pin createdAt via reflection so both invocations canonicalise identically.
        setCreatedAt(e, Instant.parse("2026-01-01T00:00:00Z"));

        byte[] a = h.computeRowHash(prev, 1L, e);
        byte[] b = h.computeRowHash(prev, 1L, e);

        assertThat(a).hasSize(32);
        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("Changing any field produces a different row_hash (avalanche)")
    void avalanche() {
        var h = new AuditChainHasher(TEST_KEY_B64);
        byte[] prev = new byte[32];
        var e1 = sampleEntity();
        setCreatedAt(e1, Instant.parse("2026-01-01T00:00:00Z"));

        var e2 = new AuditLogEntity(
                e1.getId(), e1.getUserId(), e1.getUsername(),
                e1.getAction() + "_X",        // ← changed
                e1.getResourceType(), e1.getResourceId(), e1.getDetails(),
                e1.getIpAddress(), e1.getHttpMethod(), e1.getRequestUri(),
                e1.getResponseStatus(), e1.getDurationMs());
        setCreatedAt(e2, Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(h.computeRowHash(prev, 1L, e1))
                .isNotEqualTo(h.computeRowHash(prev, 1L, e2));
    }

    @Test
    @DisplayName("Changing chain_seq produces a different row_hash (re-order resistance)")
    void chainSeqBound() {
        var h = new AuditChainHasher(TEST_KEY_B64);
        byte[] prev = new byte[32];
        var e = sampleEntity();
        setCreatedAt(e, Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(h.computeRowHash(prev, 1L, e))
                .isNotEqualTo(h.computeRowHash(prev, 2L, e));
    }

    @Test
    @DisplayName("Changing prev_hash produces a different row_hash (link dependency)")
    void prevHashBound() {
        var h = new AuditChainHasher(TEST_KEY_B64);
        var e = sampleEntity();
        setCreatedAt(e, Instant.parse("2026-01-01T00:00:00Z"));

        byte[] prev1 = new byte[32];
        byte[] prev2 = new byte[32];
        Arrays.fill(prev2, (byte) 1);

        assertThat(h.computeRowHash(prev1, 1L, e))
                .isNotEqualTo(h.computeRowHash(prev2, 1L, e));
    }

    @Test
    @DisplayName("Different keys produce different HMACs (no-key-no-forge)")
    void keyBound() {
        byte[] key2 = new byte[32];
        Arrays.fill(key2, (byte) 0x55);
        var h1 = new AuditChainHasher(TEST_KEY_B64);
        var h2 = new AuditChainHasher(Base64.getEncoder().encodeToString(key2));

        byte[] prev = new byte[32];
        var e = sampleEntity();
        setCreatedAt(e, Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(h1.computeRowHash(prev, 1L, e))
                .isNotEqualTo(h2.computeRowHash(prev, 1L, e));
    }

    @Test
    @DisplayName("Empty key triggers dev-fallback with warn flag set")
    void devFallback() {
        var h = new AuditChainHasher("");
        assertThat(h.isUsingFallback()).isTrue();
    }

    @Test
    @DisplayName("Short key (< 32 bytes) is rejected at startup (PCI DSS §3.6 / NIST SP 800-107)")
    void shortKeyRejected() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        assertThatThrownBy(() -> new AuditChainHasher(tooShort))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("≥ 32 bytes");
    }

    @Test
    @DisplayName("prev_hash of wrong length is rejected by the hasher")
    void badPrevHashLength() {
        var h = new AuditChainHasher(TEST_KEY_B64);
        assertThatThrownBy(() -> h.computeRowHash(new byte[31], 1L, sampleEntity()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("close() zeroises the in-memory key (via keyring)")
    void closeZeroises() throws Exception {
        var h = new AuditChainHasher(TEST_KEY_B64);
        // the key now lives inside an AuditChainKeyRing held by the
        // hasher. Reach through to verify zeroisation propagates.
        java.lang.reflect.Field ringField = AuditChainHasher.class.getDeclaredField("keyRing");
        ringField.setAccessible(true);
        AuditChainKeyRing ring = (AuditChainKeyRing) ringField.get(h);
        java.lang.reflect.Field keysField = AuditChainKeyRing.class.getDeclaredField("keys");
        keysField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<Integer, byte[]> keys = (java.util.Map<Integer, byte[]>) keysField.get(ring);
        byte[] key = keys.values().iterator().next();
        assertThat(key).isNotEqualTo(new byte[key.length]);
        h.close();
        assertThat(key).containsOnly((byte) 0);
    }

    // ── Helpers ──────────────────────────────────────────────

    private static void setCreatedAt(AuditLogEntity e, Instant when) {
        try {
            java.lang.reflect.Field f = AuditLogEntity.class.getDeclaredField("createdAt");
            f.setAccessible(true);
            f.set(e, when);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}


