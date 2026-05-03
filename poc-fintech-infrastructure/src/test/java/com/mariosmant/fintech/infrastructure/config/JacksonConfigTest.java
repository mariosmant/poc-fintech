package com.mariosmant.fintech.infrastructure.config;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JacksonConfig} — verifies NIST/SOC 2 hardening.
 *
 * <p>Uses Jackson 3.x ({@code tools.jackson}) exception types:
 * {@link JacksonException} replaces {@code JsonProcessingException},
 * {@link DatabindException} replaces {@code JsonMappingException}.</p>
 *
 * <p>These tests ensure the ObjectMapper rejects malicious payloads and
 * serialises data in a secure, deterministic manner.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
class JacksonConfigTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new JacksonConfig().objectMapper();
    }

    // ── Deserialisation hardening tests ──────────────────────────────────

    @Nested
    @DisplayName("Deserialisation Security")
    class DeserialisationSecurity {

        @Test
        @DisplayName("Should reject unknown properties (mass-assignment prevention)")
        void shouldRejectUnknownProperties() {
            String json = """
                    {"name": "Alice", "maliciousField": "exploit"}
                    """;
            assertThatThrownBy(() -> objectMapper.readValue(json, SimpleDto.class))
                    .isInstanceOf(UnrecognizedPropertyException.class);
        }

        @Test
        @DisplayName("Should reject duplicate JSON keys (collision attack prevention)")
        void shouldRejectDuplicateKeys() {
            String json = """
                    {"name": "Alice", "name": "Bob"}
                    """;
            assertThatThrownBy(() -> objectMapper.readValue(json, SimpleDto.class))
                    .isInstanceOf(JacksonException.class);
        }

        @Test
        @DisplayName("Should reject trailing tokens after valid JSON")
        void shouldRejectTrailingTokens() {
            String json = """
                    {"name": "Alice"}{"name": "Bob"}
                    """;
            assertThatThrownBy(() -> objectMapper.readValue(json, SimpleDto.class))
                    .isInstanceOf(DatabindException.class);
        }

        @Test
        @DisplayName("Should reject null for primitive fields")
        void shouldRejectNullForPrimitives() {
            String json = """
                    {"count": null}
                    """;
            assertThatThrownBy(() -> objectMapper.readValue(json, PrimitiveDto.class))
                    .isInstanceOf(DatabindException.class);
        }
    }

    // ── Serialisation hardening tests ────────────────────────────────────

    @Nested
    @DisplayName("Serialisation Security")
    class SerialisationSecurity {

        @Test
        @DisplayName("Should serialise dates as ISO-8601 strings, not timestamps")
        void shouldSerialiseDatesAsIso8601() throws Exception {
            Instant now = Instant.parse("2026-01-15T10:30:00Z");
            String json = objectMapper.writeValueAsString(new InstantDto(now));
            assertThat(json).contains("2026-01-15T10:30:00Z");
            assertThat(json).doesNotContain("1736935800"); // not a unix timestamp
        }

        @Test
        @DisplayName("Should write BigDecimal without scientific notation")
        void shouldWriteBigDecimalAsPlain() throws Exception {
            BigDecimal amount = new BigDecimal("0.0000001");
            String json = objectMapper.writeValueAsString(new AmountDto(amount));
            assertThat(json).contains("0.0000001");
            assertThat(json).doesNotContain("E");
        }

        @Test
        @DisplayName("Should omit null fields (information leakage prevention)")
        void shouldOmitNulls() throws Exception {
            String json = objectMapper.writeValueAsString(new SimpleDto("Alice"));
            assertThat(json).doesNotContain("null");
        }
    }

    // ── Test DTOs ────────────────────────────────────────────────────────

    /** Simple DTO for testing unknown property rejection. */
    record SimpleDto(String name) {
    }

    /** DTO with a primitive field for null-rejection testing. */
    record PrimitiveDto(int count) {
    }

    /** DTO with Instant for date serialisation testing. */
    record InstantDto(Instant timestamp) {
    }

    /** DTO with BigDecimal for financial amount testing. */
    record AmountDto(BigDecimal amount) {
    }
}
