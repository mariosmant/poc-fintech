package com.mariosmant.fintech.testcontainers;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers configuration shared across all integration tests.
 *
 * <p>Uses Spring Boot 4.x {@code @ServiceConnection} for automatic
 * property wiring — no manual datasource URL configuration needed.</p>
 *
 * <p><b>Note:</b> Testcontainers 2.0 introduced {@link ConfluentKafkaContainer}
 * (replacing the legacy {@code org.testcontainers.containers.KafkaContainer}).
 * Spring Boot 4's {@code ConfluentKafkaContainerConnectionDetailsFactory}
 * requires this new class for {@code @ServiceConnection} auto-detection.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("fintech_test")
                .withUsername("test")
                .withPassword("test");
    }

    // TODO Revise.
    @Bean
    @ServiceConnection
    public ConfluentKafkaContainer kafkaContainer() {
        return new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));
    }
}

