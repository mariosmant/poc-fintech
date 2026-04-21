package com.mariosmant.fintech.web;

import tools.jackson.databind.ObjectMapper;
import com.mariosmant.fintech.application.dto.TransferResponse;
import com.mariosmant.fintech.application.usecase.InitiateTransferUseCase;
import com.mariosmant.fintech.application.usecase.TransferQueryUseCase;
import com.mariosmant.fintech.domain.model.vo.Currency;
import com.mariosmant.fintech.infrastructure.web.controller.TransferController;
import com.mariosmant.fintech.infrastructure.web.dto.InitiateTransferRequest;
import com.mariosmant.fintech.infrastructure.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Spring MVC slice test for {@link TransferController}.
 * Lives in the boot module where {@code @SpringBootApplication} is available.
 *
 * <p>Uses {@code addFilters = false} to bypass the security filter chain,
 * but imports {@link MethodSecurityTestConfig} to activate the {@code @PreAuthorize}
 * interceptor inside the slice. {@link GlobalExceptionHandler} is imported so that
 * {@code AccessDeniedException} thrown by method security is translated to a 403
 * RFC 7807 response — the same contract the production filter chain provides.</p>
 *
 * <p>Each test manually seeds a {@link JwtAuthenticationToken} in the
 * {@link SecurityContextHolder} with an explicit authority set, to exercise
 * both happy-path ({@code ROLE_USER}) and denial-path (wrong role / no auth)
 * behaviour of class-level {@code @PreAuthorize("hasRole('USER')")}.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@WebMvcTest(value = TransferController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({TransferControllerTest.MethodSecurityTestConfig.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class TransferControllerTest {

    /**
     * Minimal configuration that turns on {@code @PreAuthorize} processing inside the
     * {@code @WebMvcTest} slice. Declared as a nested {@code @TestConfiguration} so
     * it only applies to this test and doesn't leak into other slices.
     */
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig { }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private InitiateTransferUseCase initiateTransferUseCase;
    @MockitoBean private TransferQueryUseCase transferQueryUseCase;
    @MockitoBean private JwtDecoder jwtDecoder;

    private static Jwt sampleJwt() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("test-user-123")
                .claim("preferred_username", "test-user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    /** Authenticate with a single {@code ROLE_USER} authority (default happy-path). */
    private static void authenticateAs(String... roles) {
        var authorities = java.util.Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(sampleJwt(), authorities));
    }

    @BeforeEach
    void setUp() {
        // Default: authenticated with ROLE_USER so @PreAuthorize("hasRole('USER')") passes.
        authenticateAs("ROLE_USER");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/v1/transfers with ROLE_USER → 201 CREATED")
    void shouldCreateTransfer() throws Exception {
        UUID src = UUID.randomUUID(), tgt = UUID.randomUUID(), tid = UUID.randomUUID();
        var resp = new TransferResponse(tid, "INITIATED", src, "DE89500105170000000001",
                tgt, "DE89500105170000000002",
                new BigDecimal("500.00"), "USD", null, "EUR", null, null, "k1");
        when(initiateTransferUseCase.handle(any())).thenReturn(resp);

        var req = new InitiateTransferRequest(src, tgt, null, new BigDecimal("500.00"),
                Currency.USD, Currency.EUR, "k1");

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(tid.toString()))
                .andExpect(jsonPath("$.status").value("INITIATED"));
    }

    @Test
    @DisplayName("GET /api/v1/transfers/{id} with ROLE_USER → 200 OK")
    void shouldGetTransfer() throws Exception {
        UUID id = UUID.randomUUID();
        var resp = new TransferResponse(id, "COMPLETED", UUID.randomUUID(),
                "DE89500105170000000001", UUID.randomUUID(), "DE89500105170000000002",
                new BigDecimal("100.00"), "USD", new BigDecimal("92.50"), "EUR",
                new BigDecimal("0.925"), null, "k2");
        // Non-admin caller takes the user-scoped code path.
        when(transferQueryUseCase.findByIdForUser(id, "test-user-123")).thenReturn(resp);

        mockMvc.perform(get("/api/v1/transfers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("POST /api/v1/transfers with empty body → 400")
    void shouldReturn400OnInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── @PreAuthorize / role-mapping tests ────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/transfers without ROLE_USER (e.g. ROLE_GUEST) → 403")
    void shouldDenyWhenRoleIsMissing() throws Exception {
        authenticateAs("ROLE_GUEST"); // authenticated, but wrong role

        mockMvc.perform(get("/api/v1/transfers/{id}", UUID.randomUUID()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access Denied"))
                // RFC 7807 canonical type URI, set by ProblemDetails helper
                .andExpect(jsonPath("$.type").value("urn:fintech:error:forbidden"))
                // Instance is the current request URI
                .andExpect(jsonPath("$.instance").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("GET /api/v1/transfers with empty authority set → 403")
    void shouldDenyWhenAuthoritiesEmpty() throws Exception {
        // Token present but with no granted authorities at all — simulates a JWT
        // whose realm_access.roles was missing entirely.
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(sampleJwt(), List.of()));

        mockMvc.perform(get("/api/v1/transfers/{id}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/transfers without SecurityContext → 401")
    void shouldDenyWhenUnauthenticated() throws Exception {
        SecurityContextHolder.clearContext(); // no Authentication at all

        var req = new InitiateTransferRequest(
                UUID.randomUUID(), UUID.randomUUID(), null,
                new BigDecimal("10.00"), Currency.USD, Currency.EUR, "k-deny");

        // No credentials at all → 401 Unauthorized (distinct from 403 "wrong role").
        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }
}


