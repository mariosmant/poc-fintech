package com.mariosmant.fintech.domain.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@link IbanMasking} — PCI DSS v4.0.1 §3.3 analogue.
 * Ensures sensitive characters of the IBAN are never surfaced in logs/UI.
 */
class IbanMaskingTest {

    @ParameterizedTest
    @CsvSource({
            "GB82WEST12345698765432,       GB82 **** **** **** **** 5432",
            "DE89370400440532013000,       DE89 **** **** **** **** 3000",
            "GR1601101250000000012300695,  GR16 **** **** **** **** **** 0695"
    })
    @DisplayName("mask() preserves CC+check and last 4, redacts middle in groups of 4")
    void masksCorrectly(String iban, String expected) {
        assertThat(IbanMasking.mask(iban)).isEqualTo(expected);
    }

    @Test
    @DisplayName("mask() is tolerant of spaces in the input")
    void strippsSpaces() {
        assertThat(IbanMasking.mask("GB82 WEST 1234 5698 7654 32"))
                .isEqualTo("GB82 **** **** **** **** 5432");
    }

    @Test
    @DisplayName("maskCompact() yields CC+**** + last4 with no whitespace")
    void maskCompactShape() {
        assertThat(IbanMasking.maskCompact("GB82WEST12345698765432"))
                .isEqualTo("GB82****5432");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "XX", "GB82", "GB8212"})
    @DisplayName("mask() returns a safe '****' sentinel for null/blank/short inputs")
    void safeSentinelForBadInput(String bad) {
        assertThat(IbanMasking.mask(bad)).isEqualTo("****");
        assertThat(IbanMasking.maskCompact(bad)).isEqualTo("****");
    }

    @Test
    @DisplayName("mask() returns a safe '****' sentinel for null")
    void nullSafe() {
        assertThat(IbanMasking.mask(null)).isEqualTo("****");
        assertThat(IbanMasking.maskCompact(null)).isEqualTo("****");
    }

    @Test
    @DisplayName("masked IBAN never contains any mid-section digit of the source")
    void noLeakage() {
        String source = "GB82WEST12345698765432";
        // The sensitive middle digits must not appear in the output.
        String masked = IbanMasking.mask(source);
        assertThat(masked).doesNotContain("12345698");
        assertThat(masked).doesNotContain("765432"); // except the last 4 "5432"
    }
}

