package com.mariosmant.fintech.infrastructure.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson {@link ObjectMapper} hardening configuration aligned with
 * NIST SP 800-53 (SC-8, SI-10), SOG-IS, and SOC 2 Type II controls.
 *
 * <p>Uses Jackson 3.x ({@code tools.jackson}) with the immutable builder pattern.
 * The deprecated {@code com.fasterxml.jackson} namespace is no longer used for core/databind;
 * only {@code com.fasterxml.jackson.annotation} is retained for annotation backward
 * compatibility.</p>
 *
 * <p><b>Security hardening rationale:</b></p>
 * <ul>
 *   <li><b>DEFAULT_TYPING disabled (default):</b> Prevents polymorphic deserialization
 *       attacks (CVE-2017-7525 family). Never enable default typing with untrusted input.
 *       Aligns with OWASP A08:2021 (Software and Data Integrity Failures).</li>
 *   <li><b>FAIL_ON_UNKNOWN_PROPERTIES:</b> Rejects payloads with unexpected fields,
 *       preventing mass-assignment / over-posting attacks (OWASP A01:2021).</li>
 *   <li><b>FAIL_ON_READING_DUP_TREE_KEY:</b> Rejects duplicate JSON keys,
 *       preventing key-collision attacks and ambiguous parsing.</li>
 *   <li><b>FAIL_ON_TRAILING_TOKENS:</b> Rejects trailing content after a valid JSON value,
 *       mitigating smuggling-style attacks.</li>
 *   <li><b>WRITE_DATES_AS_TIMESTAMPS disabled (DateTimeFeature):</b> Serialises dates as
 *       ISO-8601 strings for unambiguous, timezone-aware representation (NIST SP 800-92).</li>
 *   <li><b>STRICT_DUPLICATE_DETECTION:</b> Parser-level duplicate key detection for
 *       defence-in-depth.</li>
 *   <li><b>NON_NULL inclusion:</b> Omits null fields, reducing payload size and
 *       minimising information leakage (SOC 2 CC6.1).</li>
 *   <li><b>BigDecimal as plain string:</b> Avoids scientific notation in financial
 *       amounts, ensuring deterministic precision (critical for fintech).</li>
 *   <li><b>FAIL_ON_NUMBERS_FOR_ENUMS (EnumFeature):</b> Rejects numeric enum values,
 *       preventing type confusion attacks.</li>
 *   <li><b>java.time (JSR-310) built-in:</b> Jackson 3.x has native handling of
 *       {@code java.time.*} types with ISO-8601 serialisation — no separate module needed.</li>
 * </ul>
 *
 * <p><b>Production note:</b> This configuration is intentionally strict.
 * Any relaxation must be documented and reviewed against the threat model.</p>
 *
 * @author mariosmant
 * @see <a href="https://nvd.nist.gov/vuln/detail/CVE-2017-7525">CVE-2017-7525</a>
 * @see <a href="https://owasp.org/Top10/A08_2021-Software_and_Data_Integrity_Failures/">OWASP A08:2021</a>
 * @since 1.0.0
 */
@Configuration
public class JacksonConfig {

    private static final Logger log = LoggerFactory.getLogger(JacksonConfig.class);

    /**
     * Creates a hardened {@link JsonMapper} as the primary Jackson bean.
     *
     * <p>The return type is {@link JsonMapper} (not {@link ObjectMapper}) so that
     * Spring Boot 4's {@code @ConditionalOnMissingBean(JsonMapper.class)} detects
     * this bean and skips auto-configuring its own {@code jacksonJsonMapper}.</p>
     *
     * <p>All serialisation/deserialisation across the application (REST controllers,
     * Kafka producers, outbox payload serialisation) will use this mapper.</p>
     *
     * <p>Jackson 3.x enforces immutable configuration via the builder pattern.
     * Post-construction mutation is no longer permitted.</p>
     *
     * @return the hardened JsonMapper instance
     */
    @Bean
    @Primary
    public JsonMapper objectMapper() {
        JsonMapper mapper = JsonMapper.builder()

                // ── Java Time support ────────────────────────────────────────────
                // java.time (JSR-310) support is built into jackson-databind 3.x —
                // no separate JavaTimeModule registration needed.

                // ── Serialisation hardening ──────────────────────────────────────
                // Prevent failure on empty beans (e.g., value objects with no properties)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                // Write BigDecimal without scientific notation (fintech precision)
                .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)

                // ── Date/Time hardening (DateTimeFeature — Jackson 3.x) ─────────
                // Dates as ISO-8601 strings, not numeric timestamps (NIST SP 800-92)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)

                // ── Deserialisation hardening ────────────────────────────────────
                // Reject unknown fields — prevents mass-assignment attacks (OWASP A01)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // Reject duplicate keys in JSON tree — prevents collision attacks
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                // Reject trailing tokens after valid JSON
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                // Reject null for primitives — prevents silent type coercion
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)

                // ── Enum hardening (EnumFeature — Jackson 3.x) ──────────────────
                // Reject enums from integers — prevents type confusion
                .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)

                // ── Parser hardening ─────────────────────────────────────────────
                // Strict duplicate key detection at parser level (defence-in-depth)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)

                // ── Inclusion policy ─────────────────────────────────────────────
                // Omit nulls — reduces payload size and information leakage
                .changeDefaultPropertyInclusion(v ->
                        v.withValueInclusion(JsonInclude.Include.NON_NULL))

                // DEFAULT_TYPING is NOT enabled — prevents polymorphic deser attacks.
                // Jackson 3.x is secure by default; default typing cannot be
                // activated without an explicit PolymorphicTypeValidator.

                .build();

        log.info("Jackson JsonMapper configured with NIST/SOG-IS/SOC 2 hardening [tools.jackson 3.x]");
        return mapper;
    }
}


