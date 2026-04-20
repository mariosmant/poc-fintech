package com.mariosmant.fintech.e2e;

import com.mariosmant.fintech.testcontainers.EnabledIfDockerAvailable;
import com.mariosmant.fintech.testcontainers.TestcontainersConfig;
import com.mariosmant.fintech.application.dto.AccountResponse;
import com.mariosmant.fintech.application.dto.LedgerEntryResponse;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test: Full HTTP round-trip through all API endpoints.
 *
 * <p>Uses {@link RestClient} (Spring Boot 4.x replacement for TestRestTemplate)
 * with a random port and Testcontainers.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@EnabledIfDockerAvailable
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfig.class, TestSecurityConfig.class})
@ActiveProfiles("test")
class TransferE2ETest {

    @LocalServerPort
    private int port;

    private RestClient restClient;

    @BeforeEach
    void setUp() {
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("Authorization", "Bearer test-e2e-token")
                .build();
    }

    @Test
    @DisplayName("E2E: Create accounts → Transfer → Query account → Query ledger")
    void fullE2EFlow() {
        // 1. Create source account
        var sourceReq = new CreateAccountRequest("USD", new BigDecimal("10000.00"));
        AccountResponse source = restClient.post()
                .uri("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(sourceReq)
                .retrieve()
                .body(AccountResponse.class);
        assertThat(source).isNotNull();
        assertThat(source.balance()).isEqualByComparingTo("10000.00");

        // 2. Create target account
        var targetReq = new CreateAccountRequest("USD", new BigDecimal("500.00"));
        AccountResponse target = restClient.post()
                .uri("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(targetReq)
                .retrieve()
                .body(AccountResponse.class);
        assertThat(target).isNotNull();

        // 3. Initiate transfer
        String idempotencyKey = "e2e-test-" + UUID.randomUUID();
        var transferReq = new InitiateTransferRequest(
                source.id(), target.id(), null,
                new BigDecimal("2500.00"),
                Currency.USD, Currency.USD,
                idempotencyKey
        );
        TransferResponse transfer = restClient.post()
                .uri("/api/v1/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .body(transferReq)
                .retrieve()
                .body(TransferResponse.class);
        assertThat(transfer).isNotNull();
        assertThat(transfer.status()).isEqualTo("INITIATED");

        // 4. Query transfer by ID
        TransferResponse queried = restClient.get()
                .uri("/api/v1/transfers/{id}", transfer.id())
                .retrieve()
                .body(TransferResponse.class);
        assertThat(queried).isNotNull();
        assertThat(queried.id()).isEqualTo(transfer.id());

        // 5. Query source account
        AccountResponse sourceAfter = restClient.get()
                .uri("/api/v1/accounts/{id}", source.id())
                .retrieve()
                .body(AccountResponse.class);
        assertThat(sourceAfter).isNotNull();

        // 6. Query ledger entries for account (may be empty if saga hasn't completed yet)
        ResponseEntity<List<LedgerEntryResponse>> ledgerResp = restClient.get()
                .uri("/api/v1/ledger/account/{id}", source.id())
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});
        assertThat(ledgerResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("E2E: Validation error on invalid request")
    void shouldReturnValidationError() {
        // Missing required fields — expect 4xx
        var badReq = new CreateAccountRequest("", null);
        ResponseEntity<String> resp = restClient.post()
                .uri("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(badReq)
                .retrieve()
                .onStatus(s -> s.is4xxClientError(), (req, res) -> { /* swallow */ })
                .toEntity(String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("E2E: 404 on non-existent transfer")
    void shouldReturn404ForMissingTransfer() {
        ResponseEntity<String> resp = restClient.get()
                .uri("/api/v1/transfers/{id}", UUID.randomUUID())
                .retrieve()
                .onStatus(s -> s.is4xxClientError(), (req, res) -> { /* swallow */ })
                .toEntity(String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
