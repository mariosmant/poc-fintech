package com.mariosmant.fintech.domain.util;

/**
 * IBAN masking utility — redacts the middle section of an IBAN for safe display
 * in logs, error messages, UI tooltips, and export files.
 *
 * <h2>Rationale — PCI DSS v4.0.1 §3.3 analogue</h2>
 * <p>PCI DSS §3.3 requires the Primary Account Number (PAN) to be rendered
 * unreadable when displayed, exposing at most the first six and last four digits
 * to personnel with a legitimate need to see the full value.
 * Fintech account identifiers (IBAN) are not technically PANs, but they are
 * equally sensitive under:</p>
 * <ul>
 *   <li>GDPR Article 32 — "pseudonymisation and encryption of personal data"</li>
 *   <li>ISO/IEC 27001 A.8.11 — "data masking"</li>
 *   <li>PSD2 SCA — recipient-account identifier is tied to fraud-risk decisions</li>
 * </ul>
 *
 * <h2>Masking rule</h2>
 * <p>We keep the <b>country + check digits</b> (first 4 characters, publicly
 * derivable) and the <b>last 4 characters</b> (needed for user-recognition in
 * confirmation dialogs and statements). Everything in between is replaced by
 * asterisks grouped in fours to preserve the common IBAN visual cadence:</p>
 * <pre>
 *   GB82 WEST 1234 5698 7654 32  →  GB82 **** **** **** **** 5432
 *   DE89 3704 0044 0532 0130 00  →  DE89 **** **** **** **** 3000
 *   FR14 2004 1010 0505 0001 3M02 606  →  FR14 **** **** **** **** **** 2606
 * </pre>
 *
 * <p><b>Thread-safety:</b> all methods are stateless and thread-safe.</p>
 * <p><b>Null-safety:</b> {@code null}, blank, or too-short inputs return the
 * literal sentinel {@code "****"} — never throw, never reveal the original.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public final class IbanMasking {

    /** Public-prefix length: ISO 13616 country code + check digits. */
    private static final int PREFIX_LEN = 4;
    /** Suffix length retained for user recognition. */
    private static final int SUFFIX_LEN = 4;
    /** Sentinel returned for any value we refuse to display. */
    private static final String SAFE_FALLBACK = "****";

    private IbanMasking() {
    }

    /**
     * Mask the IBAN as {@code CCkk **** ... **** nnnn}.
     *
     * @param iban the IBAN (may contain spaces or be {@code null})
     * @return the masked IBAN, or {@link #SAFE_FALLBACK} when input is
     *         {@code null}, blank, or shorter than {@value #PREFIX_LEN} +
     *         {@value #SUFFIX_LEN} characters.
     */
    public static String mask(String iban) {
        if (iban == null) return SAFE_FALLBACK;
        String compact = iban.replace(" ", "").toUpperCase();
        if (compact.length() < PREFIX_LEN + SUFFIX_LEN) {
            return SAFE_FALLBACK;
        }
        String prefix = compact.substring(0, PREFIX_LEN);
        String suffix = compact.substring(compact.length() - SUFFIX_LEN);
        int middleLen = compact.length() - PREFIX_LEN - SUFFIX_LEN;
        // Round-up to nearest 4 so the asterisk groups always align.
        int groups = (middleLen + 3) / 4;
        StringBuilder sb = new StringBuilder(prefix.length() + groups * 5 + 1 + suffix.length());
        sb.append(prefix);
        for (int i = 0; i < groups; i++) {
            sb.append(' ').append("****");
        }
        sb.append(' ').append(suffix);
        return sb.toString();
    }

    /**
     * Mask an IBAN for MDC / structured log field usage — no spaces, fixed shape
     * {@code CCkk****nnnn}. Ideal when the downstream log sink tokenises on
     * whitespace or when cardinality must be bounded (observability metrics).
     *
     * @param iban the IBAN
     * @return the compact masked form, or {@link #SAFE_FALLBACK}
     */
    public static String maskCompact(String iban) {
        if (iban == null) return SAFE_FALLBACK;
        String compact = iban.replace(" ", "").toUpperCase();
        if (compact.length() < PREFIX_LEN + SUFFIX_LEN) {
            return SAFE_FALLBACK;
        }
        return compact.substring(0, PREFIX_LEN)
                + "****"
                + compact.substring(compact.length() - SUFFIX_LEN);
    }
}

