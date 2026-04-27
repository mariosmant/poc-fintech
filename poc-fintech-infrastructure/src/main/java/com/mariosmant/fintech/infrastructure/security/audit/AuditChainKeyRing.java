package com.mariosmant.fintech.infrastructure.security.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-key HMAC-SHA256 keyring for the {@code audit_log} tamper-evidence
 * chain.
 *
 * <h2>Why a keyring (not a single key)</h2>
 * <p>PCI DSS v4.0.1 §3.7 and NIST SP 800-57 Part 1 §5 require a documented
 * cryptoperiod with planned rotation and an emergency-rotation path. With a
 * single in-memory key, rotation invalidates every historical {@code row_hash}
 * and turns the verifier permanently red. A keyring lets old rows verify under
 * the key that signed them while new rows pick up a freshly-rotated active key.</p>
 *
 * <h2>Resolution order</h2>
 * <ol>
 *   <li>If {@code app.audit.chain.keys} is set (list of {@code id:base64} pairs),
 *       parse it. {@code app.audit.chain.active-key-id} picks the writer's key.</li>
 *   <li>Else if the legacy {@code app.audit.chain.hmac-key-base64} is set, it
 *       becomes the single key with {@code id=1} (active).</li>
 *   <li>Else the dev fallback key takes id=1 — loud warning at startup,
 *       refused under the {@code prod} profile by config validation.</li>
 * </ol>
 *
 * <h2>Memory hygiene</h2>
 * <p>Every key is held as a {@code byte[]} so {@link #close()} can zeroise
 * each entry on shutdown (NIST SP 800-88, SOG-IS memory-hygiene guidance).
 * The {@link Mac} instance is re-initialised per call (it is not thread-safe).</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Component
public class AuditChainKeyRing implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AuditChainKeyRing.class);
    private static final String ALG = "HmacSHA256";
    private static final int MIN_KEY_BYTES = 32;
    /** Development fallback — rejected under prod profile; published openly in source. */
    private static final String DEV_FALLBACK_B64 =
            Base64.getEncoder().encodeToString("dev-only-audit-chain-key-change-me!!".getBytes(StandardCharsets.UTF_8));

    /** Insertion-ordered map: id → key bytes. */
    private final Map<Integer, byte[]> keys;
    private final int activeKeyId;
    private final boolean usingFallback;

    @Autowired
    public AuditChainKeyRing(
            @Value("${app.audit.chain.hmac-key-base64:}") String legacyKeyBase64,
            @Value("${app.audit.chain.active-key-id:1}") int activeKeyId,
            @Value("${app.audit.chain.keys:}") String keysCsv) {

        Map<Integer, byte[]> ring = parseKeysCsv(keysCsv);

        if (ring.isEmpty() && legacyKeyBase64 != null && !legacyKeyBase64.isBlank()) {
            // single key, id=1.
            byte[] key = Base64.getDecoder().decode(legacyKeyBase64);
            assertMinLength(1, key);
            ring.put(1, key);
        }

        if (ring.isEmpty()) {
            // Dev fallback — id=1.
            ring.put(1, Base64.getDecoder().decode(DEV_FALLBACK_B64));
            this.usingFallback = true;
            log.warn("AuditChainKeyRing is using the DEV fallback key (id=1) — "
                    + "configure AUDIT_CHAIN_HMAC_KEY or app.audit.chain.keys "
                    + "in any non-local deployment.");
        } else {
            this.usingFallback = false;
        }

        if (!ring.containsKey(activeKeyId)) {
            throw new IllegalStateException(
                    "app.audit.chain.active-key-id=" + activeKeyId
                            + " but no such key exists in the ring. Known ids: " + ring.keySet());
        }

        this.keys = ring;
        this.activeKeyId = activeKeyId;
        log.info("AuditChainKeyRing initialised — known key ids={} active={}",
                ring.keySet(), activeKeyId);
    }

    /** Visible-for-tests constructor: takes a pre-built ring. */
    AuditChainKeyRing(Map<Integer, byte[]> ring, int activeKeyId, boolean usingFallback) {
        if (!ring.containsKey(activeKeyId)) {
            throw new IllegalStateException("active key id " + activeKeyId + " not in ring");
        }
        this.keys = new LinkedHashMap<>(ring);
        this.activeKeyId = activeKeyId;
        this.usingFallback = usingFallback;
    }

    /** Active key id used by the writer for new rows. */
    public int activeKeyId() {
        return activeKeyId;
    }

    /** {@code true} when the ring fell back to the published dev key. */
    public boolean isUsingFallback() {
        return usingFallback;
    }

    /** All key ids in the ring (read-only). */
    public List<Integer> knownKeyIds() {
        return List.copyOf(keys.keySet());
    }

    /**
     * Compute HMAC-SHA256 of {@code message} with the key identified by {@code keyId}.
     *
     * @throws UnknownKeyException if {@code keyId} is not in the ring.
     */
    public byte[] hmac(int keyId, byte[] message) {
        byte[] key = keys.get(keyId);
        if (key == null) {
            throw new UnknownKeyException(keyId, keys.keySet());
        }
        try {
            Mac mac = Mac.getInstance(ALG);
            mac.init(new SecretKeySpec(key, ALG));
            return mac.doFinal(message);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    /** Zeroise every key on shutdown (NIST SP 800-88). */
    @Override
    public void close() {
        for (byte[] k : keys.values()) {
            Arrays.fill(k, (byte) 0);
        }
    }

    // ── Parsing helpers ───────────────────────────────────────────────

    private static Map<Integer, byte[]> parseKeysCsv(String csv) {
        Map<Integer, byte[]> out = new LinkedHashMap<>();
        if (csv == null || csv.isBlank()) {
            return out;
        }
        for (String entry : csv.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) continue;
            int sep = trimmed.indexOf(':');
            if (sep < 1) {
                throw new IllegalStateException(
                        "app.audit.chain.keys entry '" + trimmed + "' is not in 'id:base64' form");
            }
            int id;
            try {
                id = Integer.parseInt(trimmed.substring(0, sep).trim());
            } catch (NumberFormatException nfe) {
                throw new IllegalStateException("Invalid key id in '" + trimmed + "'", nfe);
            }
            byte[] key;
            try {
                key = Base64.getDecoder().decode(trimmed.substring(sep + 1).trim());
            } catch (IllegalArgumentException iae) {
                throw new IllegalStateException("Invalid Base64 key for id=" + id, iae);
            }
            assertMinLength(id, key);
            if (out.put(id, key) != null) {
                throw new IllegalStateException("Duplicate audit-chain key id " + id);
            }
        }
        return out;
    }

    private static void assertMinLength(int id, byte[] key) {
        if (key.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "audit-chain key id=" + id + " must decode to ≥ " + MIN_KEY_BYTES
                            + " bytes (PCI DSS §3.6 / NIST SP 800-107). Got: " + key.length);
        }
    }

    /** Thrown when a row references a key id that the running app does not know. */
    public static final class UnknownKeyException extends RuntimeException {
        public UnknownKeyException(int keyId, java.util.Set<Integer> known) {
            super("Audit row signed with unknown key_id=" + keyId
                    + " (ring has " + known + ") — rotate forward, do not retire too early.");
        }
    }
}



