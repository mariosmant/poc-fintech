package com.mariosmant.fintech.domain.util;

import java.math.BigInteger;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * ISO 13616 IBAN helper for the fintech POC.
 *
 * <p>Generates valid 22-character demo German IBANs (country {@code DE}) that
 * satisfy the mod-97 check-digit rule, and validates externally-supplied IBANs
 * from a third-party transfer form. The generator is deterministic for a given
 * account UUID, making demos reproducible.</p>
 *
 * <p><b>Note:</b> the bank code used is the publicly documented Deutsche Bank
 * demo BLZ ({@code 50010517}) — this is not real banking routing. For a
 * production rollout swap in your institution's BIC/BLZ configuration.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public final class IbanUtil {

    private static final String COUNTRY_CODE = "DE";
    /** Demo German bank identifier (Deutsche Bank AG, Frankfurt). */
    private static final String BANK_CODE = "50010517";
    private static final BigInteger NINETY_EIGHT = BigInteger.valueOf(98);
    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);
    private static final BigInteger TEN_POW_10 = BigInteger.TEN.pow(10);

    /** Strict IBAN shape (country + 2 check digits + up to 30 BBAN chars). */
    private static final Pattern IBAN_SHAPE =
            Pattern.compile("^[A-Z]{2}\\d{2}[A-Z0-9]{11,30}$");

    private IbanUtil() { /* utility */ }

    /**
     * Deterministically builds a valid {@code DE}-country IBAN for an account UUID.
     *
     * @param accountId the account's UUID (primary key)
     * @return a 22-character IBAN with valid mod-97 check digits
     */
    public static String generate(UUID accountId) {
        BigInteger msb = new BigInteger(Long.toUnsignedString(accountId.getMostSignificantBits()));
        BigInteger lsb = new BigInteger(Long.toUnsignedString(accountId.getLeastSignificantBits()));
        BigInteger combined = msb.xor(lsb).abs().mod(TEN_POW_10);
        String accountNumber = String.format("%010d", combined);
        String bban = BANK_CODE + accountNumber;
        String checkDigits = computeCheckDigits(bban);
        return COUNTRY_CODE + checkDigits + bban;
    }

    /** Computes the two ISO 13616 check digits for a given BBAN and country {@code DE}. */
    private static String computeCheckDigits(String bban) {
        String rearranged = bban + toNumericCountry(COUNTRY_CODE) + "00";
        BigInteger n = new BigInteger(rearranged);
        int check = NINETY_EIGHT.subtract(n.mod(NINETY_SEVEN)).intValueExact();
        return String.format("%02d", check);
    }

    private static String toNumericCountry(String country) {
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < country.length(); i++) {
            sb.append((country.charAt(i) - 'A') + 10);
        }
        return sb.toString();
    }

    /**
     * Validates an IBAN: shape, length (country-specific for DE=22) and mod-97.
     * Input may contain spaces; they are stripped prior to validation.
     *
     * @param iban the candidate IBAN (nullable)
     * @return {@code true} when the IBAN is structurally valid and mod-97 passes
     */
    public static boolean isValid(String iban) {
        if (iban == null) return false;
        String normalized = normalize(iban);
        if (!IBAN_SHAPE.matcher(normalized).matches()) return false;
        if (normalized.startsWith(COUNTRY_CODE) && normalized.length() != 22) return false;
        String rearranged = normalized.substring(4) + normalized.substring(0, 4);
        StringBuilder numeric = new StringBuilder(rearranged.length() * 2);
        for (int i = 0; i < rearranged.length(); i++) {
            char c = rearranged.charAt(i);
            if (c >= '0' && c <= '9') {
                numeric.append(c);
            } else if (c >= 'A' && c <= 'Z') {
                numeric.append((c - 'A') + 10);
            } else {
                return false;
            }
        }
        try {
            return new BigInteger(numeric.toString()).mod(NINETY_SEVEN).intValueExact() == 1;
        } catch (ArithmeticException ex) {
            return false;
        }
    }

    /** Removes whitespace and uppercases an IBAN for canonical storage/comparison. */
    public static String normalize(String iban) {
        if (iban == null) return null;
        return iban.replaceAll("\\s+", "").toUpperCase();
    }
}

