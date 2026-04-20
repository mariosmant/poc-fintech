package com.mariosmant.fintech.infrastructure.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Cryptographically safe secret handling utility (NIST SP 800-132, SOG-IS).
 *
 * <p><b>Key principles:</b></p>
 * <ul>
 *   <li>Secrets stored as {@code byte[]} or {@code char[]} — <b>never</b> as {@code String}
 *       (String is immutable, cannot be zeroed, lingers in heap/intern pool)</li>
 *   <li>All secret material is zeroed after use via {@link #zeroize(byte[])} / {@link #zeroize(char[])}</li>
 *   <li>HMAC-SHA3-256 for message authentication (NIST FIPS 202)</li>
 *   <li>CSPRNG ({@link SecureRandom}) for all random generation</li>
 * </ul>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public final class SecureSecretUtils {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final SecureRandom CSPRNG = new SecureRandom();

    private SecureSecretUtils() { /* utility */ }

    /**
     * Computes HMAC-SHA256 of the given data using a secret key.
     * Key material is zeroed after use.
     *
     * @param key  the secret key (will be zeroed after use)
     * @param data the data to authenticate
     * @return hex-encoded HMAC
     */
    public static String hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(key, HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] result = mac.doFinal(data);
            return HexFormat.of().formatHex(result);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC computation failed", e);
        } finally {
            zeroize(key);
        }
    }

    /**
     * Converts char[] to byte[] using UTF-8, then zeroes the char[].
     * Use this to safely convert password/secret char arrays.
     *
     * @param chars the character array (will be zeroed after conversion)
     * @return the byte representation
     */
    public static byte[] charsToBytes(char[] chars) {
        try {
            CharBuffer charBuffer = CharBuffer.wrap(chars);
            ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(charBuffer);
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
            // Zero the intermediate ByteBuffer
            byteBuffer.clear();
            while (byteBuffer.hasRemaining()) {
                byteBuffer.put((byte) 0);
            }
            return bytes;
        } finally {
            zeroize(chars);
        }
    }

    /**
     * Generates a cryptographically secure random byte array.
     *
     * @param length the number of random bytes
     * @return random bytes from CSPRNG
     */
    public static byte[] generateSecureRandom(int length) {
        byte[] bytes = new byte[length];
        CSPRNG.nextBytes(bytes);
        return bytes;
    }

    /**
     * Generates a secure random token as hex string.
     *
     * @param byteLength number of random bytes (hex output will be 2x this)
     * @return hex-encoded random token
     */
    public static String generateSecureToken(int byteLength) {
        return HexFormat.of().formatHex(generateSecureRandom(byteLength));
    }

    /**
     * Constant-time comparison to prevent timing attacks.
     *
     * @param a first byte array
     * @param b second byte array
     * @return true if arrays are equal
     */
    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    /**
     * Zeroes a byte array to prevent secret leakage in memory.
     * Must be called in a finally block after secret usage.
     *
     * @param secret the byte array to zero
     */
    public static void zeroize(byte[] secret) {
        if (secret != null) {
            Arrays.fill(secret, (byte) 0);
        }
    }

    /**
     * Zeroes a char array to prevent secret leakage in memory.
     *
     * @param secret the char array to zero
     */
    public static void zeroize(char[] secret) {
        if (secret != null) {
            Arrays.fill(secret, '\0');
        }
    }
}

