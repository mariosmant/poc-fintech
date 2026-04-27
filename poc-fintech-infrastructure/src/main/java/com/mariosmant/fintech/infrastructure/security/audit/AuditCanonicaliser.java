package com.mariosmant.fintech.infrastructure.security.audit;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical byte-serialiser for {@link AuditLogEntity} — produces a
 * deterministic, locale-/platform-/JVM-independent byte representation
 * that is fed into the HMAC chain.
 *
 * <h2>Why a bespoke canonicaliser (not JSON, not Java serialization)</h2>
 * <ul>
 *   <li><b>Stability:</b> the bytes must be identical across Java versions,
 *       Jackson upgrades, locale changes, and JVM vendor swaps. Any drift
 *       silently invalidates the chain and produces false-positive
 *       verification alerts.</li>
 *   <li><b>Determinism:</b> a field order is fixed here, in source, with a
 *       unit test pinning the exact bytes for a known entity. Future schema
 *       additions go to the <i>end</i> of the canonical form (additive only)
 *       so historical rows still verify.</li>
 *   <li><b>Collision resistance:</b> each field is length-prefixed
 *       (4-byte big-endian) so no concatenation ambiguity allows
 *       {@code (a="ab", b="c")} and {@code (a="a", b="bc")} to collide.</li>
 * </ul>
 *
 * <h2>Format</h2>
 * <pre>
 *   [u32 len][bytes]           — for each field, in the order declared below
 *   NULL fields encode as       — [u32=0xFFFFFFFF]
 *   UUID fields encode as       — 16 big-endian bytes
 *   Integer/Long fields encode as 4 / 8 big-endian bytes
 *   Instant fields encode as     — ISO-8601 UTC truncated to microseconds
 * </pre>
 *
 * <p>The HMAC input is:
 * {@code prev_hash (32 B) || chain_seq (8 B BE) || canonicalRow(entity)}.
 * Including {@code chain_seq} in the HMAC blocks a re-ordering attack
 * (swap two rows but keep each row_hash valid).</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
final class AuditCanonicaliser {

    /** Sentinel for {@code null} fields in the canonical form. */
    private static final byte[] NULL_MARKER = {
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
    };

    private AuditCanonicaliser() {
    }

    /**
     * Produce the canonical byte form of the entity, excluding the chain
     * columns themselves ({@code prev_hash}, {@code row_hash},
     * {@code chain_seq}) — they are appended separately by the hasher.
     */
    static byte[] canonicalise(AuditLogEntity e) {
        Objects.requireNonNull(e, "entity");
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream(512);

        writeUuid(buf, e.getId());
        writeString(buf, e.getUserId());
        writeString(buf, e.getUsername());
        writeString(buf, e.getAction());
        writeString(buf, e.getResourceType());
        writeString(buf, e.getResourceId());
        writeString(buf, e.getDetails());
        writeString(buf, e.getIpAddress());
        writeString(buf, e.getHttpMethod());
        writeString(buf, e.getRequestUri());
        writeIntegerBoxed(buf, e.getResponseStatus());
        writeLongBoxed(buf, e.getDurationMs());
        writeInstant(buf, e.getCreatedAt());

        return buf.toByteArray();
    }

    /** Prepends the chain-seq + prev-hash to the row canonical bytes. */
    static byte[] withChainContext(byte[] prevHash, long chainSeq, byte[] rowBytes) {
        if (prevHash == null || prevHash.length != 32) {
            throw new IllegalArgumentException("prevHash must be 32 bytes");
        }
        byte[] out = new byte[32 + 8 + rowBytes.length];
        System.arraycopy(prevHash, 0, out, 0, 32);
        // chain_seq big-endian
        for (int i = 0; i < 8; i++) {
            out[32 + i] = (byte) (chainSeq >>> (56 - 8 * i));
        }
        System.arraycopy(rowBytes, 0, out, 40, rowBytes.length);
        return out;
    }

    // ── Primitive writers ──────────────────────────────────────────────

    private static void writeLen(java.io.ByteArrayOutputStream buf, int len) {
        buf.write((len >>> 24) & 0xFF);
        buf.write((len >>> 16) & 0xFF);
        buf.write((len >>> 8) & 0xFF);
        buf.write(len & 0xFF);
    }

    private static void writeString(java.io.ByteArrayOutputStream buf, String s) {
        if (s == null) {
            buf.writeBytes(NULL_MARKER);
            return;
        }
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        writeLen(buf, data.length);
        buf.writeBytes(data);
    }

    private static void writeUuid(java.io.ByteArrayOutputStream buf, UUID u) {
        if (u == null) {
            buf.writeBytes(NULL_MARKER);
            return;
        }
        writeLen(buf, 16);
        long msb = u.getMostSignificantBits();
        long lsb = u.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) buf.write((int) ((msb >>> (56 - 8 * i)) & 0xFF));
        for (int i = 0; i < 8; i++) buf.write((int) ((lsb >>> (56 - 8 * i)) & 0xFF));
    }

    private static void writeIntegerBoxed(java.io.ByteArrayOutputStream buf, Integer v) {
        if (v == null) {
            buf.writeBytes(NULL_MARKER);
            return;
        }
        writeLen(buf, 4);
        int x = v;
        buf.write((x >>> 24) & 0xFF);
        buf.write((x >>> 16) & 0xFF);
        buf.write((x >>> 8) & 0xFF);
        buf.write(x & 0xFF);
    }

    private static void writeLongBoxed(java.io.ByteArrayOutputStream buf, Long v) {
        if (v == null) {
            buf.writeBytes(NULL_MARKER);
            return;
        }
        writeLen(buf, 8);
        long x = v;
        for (int i = 0; i < 8; i++) buf.write((int) ((x >>> (56 - 8 * i)) & 0xFF));
    }

    private static void writeInstant(java.io.ByteArrayOutputStream buf, Instant t) {
        if (t == null) {
            buf.writeBytes(NULL_MARKER);
            return;
        }
        // Truncate to microseconds — Postgres TIMESTAMPTZ has µs precision,
        // Java can produce ns, the round-trip would otherwise break the HMAC.
        Instant truncated = t.plusNanos(0).with(java.time.temporal.ChronoField.NANO_OF_SECOND,
                (t.getNano() / 1_000) * 1_000);
        String s = DateTimeFormatter.ISO_INSTANT.format(truncated);
        byte[] data = s.getBytes(StandardCharsets.US_ASCII);
        writeLen(buf, data.length);
        buf.writeBytes(data);
    }
}

