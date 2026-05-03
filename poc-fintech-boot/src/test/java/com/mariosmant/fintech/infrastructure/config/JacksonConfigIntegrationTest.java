package com.mariosmant.fintech.infrastructure.config;

import com.mariosmant.fintech.infrastructure.config.JacksonConfig;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Boot integration test verifying the Jackson ObjectMapper bean
 * is correctly configured with NIST/SOC 2 hardening.
 *
 * <p>Uses {@link ApplicationContextRunner} — the Spring Boot recommended pattern
 * for testing {@code @Configuration} classes in isolation. This avoids
 * classpath conflicts between Jackson 2.x (spring-boot-jackson backward
 * compatibility) and Jackson 3.x (tools.jackson) that can occur with
 * {@code @ContextConfiguration}.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
class JacksonConfigIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JacksonConfig.class);

    @Test
    @DisplayName("ObjectMapper should have FAIL_ON_UNKNOWN_PROPERTIES enabled (mass-assignment prevention)")
    void shouldFailOnUnknownProperties() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ObjectMapper.class);
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            assertThat(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
                    .isTrue();
        });
    }

    @Test
    @DisplayName("ObjectMapper should have FAIL_ON_READING_DUP_TREE_KEY enabled")
    void shouldFailOnDupKeys() {
        contextRunner.run(context -> {
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            assertThat(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY))
                    .isTrue();
        });
    }

    @Test
    @DisplayName("ObjectMapper should NOT write dates as timestamps (DateTimeFeature)")
    void shouldNotWriteDatesAsTimestamps() {
        contextRunner.run(context -> {
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            assertThat(objectMapper.isEnabled(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS))
                    .isFalse();
        });
    }

    @Test
    @DisplayName("ObjectMapper should have FAIL_ON_NULL_FOR_PRIMITIVES enabled")
    void shouldFailOnNullPrimitives() {
        contextRunner.run(context -> {
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            assertThat(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES))
                    .isTrue();
        });
    }

    @Test
    @DisplayName("ObjectMapper should have FAIL_ON_NUMBERS_FOR_ENUMS enabled (EnumFeature)")
    void shouldFailOnNumbersForEnums() {
        contextRunner.run(context -> {
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            assertThat(objectMapper.isEnabled(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS))
                    .isTrue();
        });
    }
}

