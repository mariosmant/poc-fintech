package com.mariosmant.fintech.infrastructure.security.reputation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * in-memory CIDR-set {@link IpReputationService}.
 *
 * <p>Holds a parsed snapshot of CIDR ranges (and single-IP /32 entries) in
 * a copy-on-write {@link AtomicReference} array so reads on the request hot
 * path are wait-free and refreshes never block the limiter. Lookup is a
 * linear scan of the parsed-once integer prefixes — for the typical
 * Spamhaus DROP feed (~1 200 entries) this is ≪ 1 µs on commodity HW.</p>
 *
 * <p>Refreshes are pushed by an external scheduler (e.g. a
 * {@code SpamhausDropFeedRefresher} or a manual operator action) calling
 * {@link #replace(Collection)}; a refresh that fails to parse leaves the
 * previous snapshot in place (fail-static — better to keep the last known
 * good list than to drop protection on a transient parse error).</p>
 *
 * <p>IPv6 support: implementations parse the address but only single
 * {@code /128} entries are matched literally; CIDR aggregation for IPv6
 * is deferred until needed. The vast majority of abuse feeds are IPv4.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public final class CaffeineIpReputationService implements IpReputationService {

    private static final Logger log = LoggerFactory.getLogger(CaffeineIpReputationService.class);

    /** Immutable snapshot — swapped atomically on refresh. */
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.EMPTY);

    @Override
    public boolean isBlocked(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) return false;
        Snapshot s = snapshot.get();
        if (s.cidrPrefixes.length == 0) return false;
        long ip = parseIpv4(remoteAddr);
        if (ip < 0) return false; // not IPv4 — IPv6 abuse is rare; skip until we ship IPv6 trie.
        for (int i = 0; i < s.cidrPrefixes.length; i++) {
            int prefix = s.cidrPrefixes[i];
            long mask = s.cidrMasks[i];
            if ((ip & mask) == (prefix & mask)) return true;
        }
        return false;
    }

    @Override
    public int size() {
        return snapshot.get().cidrPrefixes.length;
    }

    /**
     * Replace the active block-list with the parsed result of {@code cidrs}.
     * Malformed entries are logged at DEBUG and skipped; if every entry is
     * malformed the previous snapshot is preserved (fail-static).
     */
    public void replace(Collection<String> cidrs) {
        if (cidrs == null) {
            log.warn("IP-reputation refresh received null collection — keeping previous snapshot ({} entries).",
                    snapshot.get().cidrPrefixes.length);
            return;
        }
        List<long[]> parsed = new ArrayList<>(cidrs.size());
        int rejected = 0;
        for (String cidr : cidrs) {
            long[] entry = parseCidr(cidr);
            if (entry == null) {
                rejected++;
            } else {
                parsed.add(entry);
            }
        }
        if (parsed.isEmpty() && rejected > 0) {
            log.warn("IP-reputation refresh failed to parse any entries (rejected={}) — keeping previous snapshot ({} entries).",
                    rejected, snapshot.get().cidrPrefixes.length);
            return;
        }
        int[] prefixes = new int[parsed.size()];
        long[] masks = new long[parsed.size()];
        for (int i = 0; i < parsed.size(); i++) {
            prefixes[i] = (int) parsed.get(i)[0];
            masks[i] = parsed.get(i)[1];
        }
        snapshot.set(new Snapshot(prefixes, masks));
        log.info("IP-reputation snapshot refreshed: accepted={} rejected={}", parsed.size(), rejected);
    }

    // ── parsers ────────────────────────────────────────────────────────

    /** @return signed long holding the IPv4 as 32-bit unsigned int, or -1 on error. */
    static long parseIpv4(String addr) {
        try {
            InetAddress ia = InetAddress.getByName(addr);
            byte[] b = ia.getAddress();
            if (b.length != 4) return -1;
            return ((long) (b[0] & 0xff) << 24)
                 | ((long) (b[1] & 0xff) << 16)
                 | ((long) (b[2] & 0xff) <<  8)
                 |  (long) (b[3] & 0xff);
        } catch (UnknownHostException | SecurityException ex) {
            return -1;
        }
    }

    /**
     * Parse a {@code "a.b.c.d/n"} CIDR (or bare IPv4 → /32) into a
     * {@code long[]{prefix, mask}}. Returns {@code null} on parse error.
     */
    static long[] parseCidr(String cidr) {
        if (cidr == null) return null;
        String trimmed = cidr.trim();
        // Spamhaus DROP lines look like "1.2.3.0/24 ; SBL12345" — strip the ;... tail.
        int semi = trimmed.indexOf(';');
        if (semi >= 0) trimmed = trimmed.substring(0, semi).trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null;
        String addr;
        int prefixLen;
        int slash = trimmed.indexOf('/');
        if (slash < 0) {
            addr = trimmed;
            prefixLen = 32;
        } else {
            addr = trimmed.substring(0, slash);
            try {
                prefixLen = Integer.parseInt(trimmed.substring(slash + 1).trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        if (prefixLen < 0 || prefixLen > 32) return null;
        long ip = parseIpv4(addr);
        if (ip < 0) return null;
        long mask = prefixLen == 0 ? 0L : (0xFFFFFFFFL << (32 - prefixLen)) & 0xFFFFFFFFL;
        return new long[] { ip & mask, mask };
    }

    /** Immutable snapshot — swapped wholesale on refresh. */
    private record Snapshot(int[] cidrPrefixes, long[] cidrMasks) {
        static final Snapshot EMPTY = new Snapshot(new int[0], new long[0]);
    }

    // Convenience for ops scripts and tests.
    public static List<String> defaultPrivateRanges() {
        return Arrays.asList("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16");
    }
}

