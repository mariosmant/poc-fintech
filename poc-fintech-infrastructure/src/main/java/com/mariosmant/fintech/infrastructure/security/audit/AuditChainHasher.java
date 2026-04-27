package com.mariosmant.fintech.infrastructure.security.audit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HMAC-SHA256 hasher for the {@code audit_log} tamper-evidence chain
 * (PCI DSS v4.0.1 §10.5, NIST AU-9(3), NIST AU-10).
 *
 * <h2>delegation to {@link AuditChainKeyRing}</h2>
 * rotation: the keyring owns the {@code id → key} map; this hasher just
 * computes the HMAC under the requested {@code keyId}. The legacy
 * {@link #computeRowHash(byte[], long, AuditLogEntity) computeRowHash}
 * entry point still exists and defaults to the active key — it is what the
 * writer calls. The verifier uses
 * {@link #computeRowHash(int, byte[], long, AuditLogEntity)} to recompute under
 * the historical key id stored on each row.</p>
 *
 * <h2>Algorithm</h2>
 * <p>HMAC-SHA256 — mandated by PCI DSS v4.0.1 cryptographic-strong-function
 * list (SP 800-107 §5), 128-bit forgery resistance, hardware-accelerated.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Component
public class AuditChainHasher implements AutoCloseable {

    private final AuditChainKeyRing keyRing;
    private final boolean ownsKeyRing;

    /** Production constructor — Spring injects the singleton keyring. */
    @Autowired
    public AuditChainHasher(AuditChainKeyRing keyRing) {
        this.keyRing = keyRing;
        this.ownsKeyRing = false;
    }

    /**
     * Legacy single-key constructor preserved for unit tests.
     * Builds a one-entry keyring with id=1 active.
     */
    public AuditChainHasher(String keyBase64) {
        this.keyRing = buildLegacyKeyRing(keyBase64);
        this.ownsKeyRing = true;
    }

    private static AuditChainKeyRing buildLegacyKeyRing(String keyBase64) {
        if (keyBase64 == null || keyBase64.isBlank()) {
            // Dev-fallback path — let the keyring's own fallback handling fire.
            return new AuditChainKeyRing("", 1, "");
        }
        // Validate length up front so the legacy "short key rejected" test still passes.
        byte[] decoded = Base64.getDecoder().decode(keyBase64);
        if (decoded.length < 32) {
            throw new IllegalStateException(
                    "app.audit.chain.hmac-key-base64 must decode to ≥ 32 bytes "
                            + "(PCI DSS §3.6 / NIST SP 800-107). Got: " + decoded.length);
        }
        Map<Integer, byte[]> ring = new LinkedHashMap<>();
        ring.put(1, decoded);
        return new AuditChainKeyRing(ring, 1, false);
    }

    /** {@code true} iff the underlying keyring is using the dev-fallback key. */
    public boolean isUsingFallback() {
        return keyRing.isUsingFallback();
    }

    /** Currently-active key id (used by the writer for new rows). */
    public int activeKeyId() {
        return keyRing.activeKeyId();
    }

    /** Compute HMAC-SHA256 of {@code message} under the <i>active</i> key. */
    public byte[] hmac(byte[] message) {
        return keyRing.hmac(keyRing.activeKeyId(), message);
    }

    /**
     * Compute the row hash under the <i>active</i> key. Used by the writer.
     */
    public byte[] computeRowHash(byte[] prevHash, long chainSeq, AuditLogEntity entity) {
        return computeRowHash(keyRing.activeKeyId(), prevHash, chainSeq, entity);
    }

    /**
     * Compute the row hash under a <i>specific</i> key id. Used by the verifier
     * to re-sign historical rows under the key that originally signed them.
     *
     * @param keyId    id of the key to use; must be present in the keyring
     * @param prevHash previous row's {@code row_hash} (32 bytes)
     * @param chainSeq sequence number of this row
     * @param entity   the audit entity whose canonical bytes are HMAC-input
     */
    public byte[] computeRowHash(int keyId, byte[] prevHash, long chainSeq, AuditLogEntity entity) {
        byte[] rowBytes = AuditCanonicaliser.canonicalise(entity);
        byte[] message = AuditCanonicaliser.withChainContext(prevHash, chainSeq, rowBytes);
        return keyRing.hmac(keyId, message);
    }

    /**
     * Zeroise key material on shutdown. Only zeroes the keyring if this hasher
     * owns it (i.e. was constructed via the legacy single-key constructor in
     * unit tests); when injected by Spring, the keyring's own lifecycle owns
     * disposal so we do not double-free its state.
     */
    @Override
    public void close() {
        if (ownsKeyRing) {
            keyRing.close();
        }
    }
}

