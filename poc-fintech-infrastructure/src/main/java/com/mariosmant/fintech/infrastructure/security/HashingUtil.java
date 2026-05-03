package com.mariosmant.fintech.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Cryptographic hashing utility using NIST-approved SHA3-256.
 *
 * <p>SHA3-256 is chosen for:
 * <ul>
 *   <li><b>NIST FIPS 202</b> compliance</li>
 *   <li><b>SOC</b> alignment — approved for use until 2030+</li>
 *   <li>Collision resistance: 128-bit security level (birthday bound)</li>
 *   <li>No length-extension attacks (unlike SHA-2)</li>
 * </ul></p>
 *
 * <p>Used for idempotency key fingerprinting and audit log hashing.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public final class HashingUtil {

    private static final String ALGORITHM = "SHA3-256";

    private HashingUtil() { /* utility class */ }

    /**
     * Computes the SHA3-256 hash of the given input string.
     *
     * @param input the string to hash
     * @return the hex-encoded hash
     * @throws IllegalStateException if SHA3-256 is not available
     */
    public static String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA3-256 algorithm not available", e);
        }
    }

    /**
     * Verifies that a given input matches an expected hash.
     *
     * @param input        the original input
     * @param expectedHash the expected hex hash
     * @return {@code true} if the hash matches
     */
    public static boolean verify(String input, String expectedHash) {
        // Constant-time comparison to prevent timing side-channel attacks (NIST SP 800-131A)
        byte[] computedBytes = hash(input).toLowerCase().getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = expectedHash.toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(computedBytes, expectedBytes);
    }
}

