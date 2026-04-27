package com.mariosmant.fintech.infrastructure.security.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AuditChainKeyRing} and the multi-key
 * {@link AuditChainHasher#computeRowHash(int, byte[], long, AuditLogEntity)}. No DB / Spring context.
 */
class AuditChainKeyRingTest {

    private static byte[] keyOf(byte b) {
        byte[] k = new byte[32];
        Arrays.fill(k, b);
        return k;
    }

    private static String b64(byte[] k) {
        return Base64.getEncoder().encodeToString(k);
    }

    private static AuditLogEntity sample() {
        AuditLogEntity e = new AuditLogEntity(
                UUID.fromString("00000000-0000-0000-0000-000000000042"),
                "u", "alice", "ACT", "Type", "rid", null,
                "127.0.0.1", "POST", "/api/x", 200, 5L);
        try {
            java.lang.reflect.Field f = AuditLogEntity.class.getDeclaredField("createdAt");
            f.setAccessible(true);
            f.set(e, Instant.parse("2026-04-25T00:00:00Z"));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return e;
    }

    @Test
    @DisplayName("CSV constructor parses 'id:base64,id:base64' and exposes both ids")
    void parsesCsv() {
        String csv = "1:" + b64(keyOf((byte) 1)) + ",2:" + b64(keyOf((byte) 2));
        var ring = new AuditChainKeyRing("", 2, csv);
        assertThat(ring.knownKeyIds()).containsExactly(1, 2);
        assertThat(ring.activeKeyId()).isEqualTo(2);
        assertThat(ring.isUsingFallback()).isFalse();
    }

    @Test
    @DisplayName("Different key ids produce different HMACs for the same message")
    void differentKeysDifferentMacs() {
        Map<Integer, byte[]> ring = new LinkedHashMap<>();
        ring.put(1, keyOf((byte) 0x11));
        ring.put(2, keyOf((byte) 0x22));
        var keyRing = new AuditChainKeyRing(ring, 1, false);
        byte[] msg = "hello".getBytes();
        assertThat(keyRing.hmac(1, msg)).isNotEqualTo(keyRing.hmac(2, msg));
    }

    @Test
    @DisplayName("Unknown key id throws UnknownKeyException with diagnostic")
    void unknownKey() {
        Map<Integer, byte[]> ring = new LinkedHashMap<>();
        ring.put(1, keyOf((byte) 1));
        var keyRing = new AuditChainKeyRing(ring, 1, false);
        assertThatThrownBy(() -> keyRing.hmac(99, new byte[1]))
                .isInstanceOf(AuditChainKeyRing.UnknownKeyException.class)
                .hasMessageContaining("unknown key_id=99");
    }

    @Test
    @DisplayName("Active key id not in ring is rejected at construction")
    void activeKeyMustExist() {
        String csv = "1:" + b64(keyOf((byte) 1));
        assertThatThrownBy(() -> new AuditChainKeyRing("", 5, csv))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active-key-id=5");
    }

    @Test
    @DisplayName("Hasher computeRowHash(keyId,...) verifies under historical key after rotation")
    void hasherVerifiesUnderHistoricalKey() {
        Map<Integer, byte[]> ring = new LinkedHashMap<>();
        ring.put(1, keyOf((byte) 1));
        ring.put(2, keyOf((byte) 2));
        var keyRing = new AuditChainKeyRing(ring, 2, false);
        var hasher = new AuditChainHasher(keyRing);

        byte[] prev = new byte[32];
        // Row 1 was signed under id=1 (before rotation).
        byte[] hash1 = hasher.computeRowHash(1, prev, 1L, sample());
        // Row 2 will be signed under the active key (id=2).
        byte[] hash2 = hasher.computeRowHash(prev, 2L, sample());

        // Different keys → different hashes.
        assertThat(hash1).isNotEqualTo(hash2);
        // computeRowHash(1, …) deterministic and reproducible — verifier path.
        assertThat(hasher.computeRowHash(1, prev, 1L, sample())).isEqualTo(hash1);
    }

    @Test
    @DisplayName("close() zeroises every key in the ring")
    void closeZeroisesAll() throws Exception {
        Map<Integer, byte[]> ring = new LinkedHashMap<>();
        byte[] k1 = keyOf((byte) 0x11);
        byte[] k2 = keyOf((byte) 0x22);
        ring.put(1, k1);
        ring.put(2, k2);
        var keyRing = new AuditChainKeyRing(ring, 1, false);
        keyRing.close();
        assertThat(k1).containsOnly((byte) 0);
        assertThat(k2).containsOnly((byte) 0);
    }

    @Test
    @DisplayName("Short key in CSV is rejected at parse time")
    void shortKeyRejected() {
        String csv = "1:" + Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        assertThatThrownBy(() -> new AuditChainKeyRing("", 1, csv))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("≥ 32 bytes");
    }
}

