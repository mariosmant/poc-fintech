package com.mariosmant.fintech.integration;

import com.mariosmant.fintech.testcontainers.EnabledIfDockerAvailable;
import com.mariosmant.fintech.testcontainers.TestcontainersConfig;
import com.mariosmant.fintech.application.dto.AccountResponse;
import com.mariosmant.fintech.application.dto.TransferResponse;
import com.mariosmant.fintech.infrastructure.web.dto.CreateAccountRequest;
import com.mariosmant.fintech.infrastructure.web.dto.InitiateTransferRequest;
import com.mariosmant.fintech.domain.model.vo.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import com.mariosmant.fintech.testcontainers.TestSecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: Full transfer happy-path with Testcontainers (Postgres + Kafka).
 *
 * <p>Uses {@link RestClient} (Spring Boot 4.x replacement for TestRestTemplate)
 * to test the complete flow: create accounts → initiate transfer → verify status.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@EnabledIfDockerAvailable
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfig.class, TestSecurityConfig.class})
@ActiveProfiles("test")
class TransferIntegrationTest {

    @LocalServerPort
    private int port;

    private RestClient restClient;

    @BeforeEach
    void setUp() {
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("Authorization", "Bearer test-integration-token")
                .build();
    }

    @Test
    @DisplayName("Should create accounts and initiate a transfer")
    void shouldCreateAccountsAndTransfer() {
        // Create source account with balance
        var sourceReq = new CreateAccountRequest("USD", new BigDecimal("5000.00"));
        ResponseEntity<AccountResponse> sourceResp = restClient.post()
                .uri("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(sourceReq)
                .retrieve()
                .toEntity(AccountResponse.class);
        assertThat(sourceResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(sourceResp.getBody()).isNotNull();
        UUID sourceId = sourceResp.getBody().id();

        // Create target account
        var targetReq = new CreateAccountRequest("EUR", new BigDecimal("1000.00"));
        ResponseEntity<AccountResponse> targetResp = restClient.post()
                .uri("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(targetReq)
                .retrieve()
                .toEntity(AccountResponse.class);
        assertThat(targetResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID targetId = targetResp.getBody().id();

        // Initiate transfer
        var transferReq = new InitiateTransferRequest(
                sourceId, targetId, null,
                new BigDecimal("500.00"),
                Currency.USD, Currency.EUR,
                "integration-test-key-" + UUID.randomUUID()
        );
        ResponseEntity<TransferResponse> transferResp = restClient.post()
                .uri("/api/v1/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .body(transferReq)
                .retrieve()
                .toEntity(TransferResponse.class);
        assertThat(transferResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(transferResp.getBody()).isNotNull();
        assertThat(transferResp.getBody().status()).isEqualTo("INITIATED");
        assertThat(transferResp.getBody().sourceAmount()).isEqualByComparingTo("500.00");

        // Verify transfer can be queried
        UUID transferId = transferResp.getBody().id();
        ResponseEntity<TransferResponse> getResp = restClient.get()
                .uri("/api/v1/transfers/{id}", transferId)
                .retrieve()
                .toEntity(TransferResponse.class);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody().id()).isEqualTo(transferId);
    }

    @Test
    @DisplayName("Should return existing transfer on duplicate idempotency key")
    void shouldReturnExistingOnDuplicateKey() {
        // Create accounts
        var sourceReq = new CreateAccountRequest("USD", new BigDecimal("3000.00"));
        AccountResponse source = restClient.post()
                .uri("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(sourceReq)
                .retrieve()
                .body(AccountResponse.class);
        var targetReq = new CreateAccountRequest("USD", new BigDecimal("0.00"));
        AccountResponse target = restClient.post()
                .uri("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(targetReq)
                .retrieve()
                .body(AccountResponse.class);

        String idempotencyKey = "dup-test-key-" + UUID.randomUUID();
        var transferReq = new InitiateTransferRequest(
                source.id(), target.id(), null,
                new BigDecimal("100.00"),
                Currency.USD, Currency.USD,
                idempotencyKey
        );

        // First request
        ResponseEntity<TransferResponse> first = restClient.post()
                .uri("/api/v1/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .body(transferReq)
                .retrieve()
                .toEntity(TransferResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second request with same key — should return same transfer
        ResponseEntity<TransferResponse> second = restClient.post()
                .uri("/api/v1/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .body(transferReq)
                .retrieve()
                .toEntity(TransferResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody().id()).isEqualTo(first.getBody().id());
    }
}
