package com.mariosmant.fintech.web;

import tools.jackson.databind.ObjectMapper;
import com.mariosmant.fintech.application.dto.TransferResponse;
import com.mariosmant.fintech.application.usecase.InitiateTransferUseCase;
import com.mariosmant.fintech.application.usecase.TransferQueryUseCase;
import com.mariosmant.fintech.domain.model.vo.Currency;
import com.mariosmant.fintech.infrastructure.web.controller.TransferController;
import com.mariosmant.fintech.infrastructure.web.dto.InitiateTransferRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Spring MVC slice test for {@link TransferController}.
 * Lives in the boot module where {@code @SpringBootApplication} is available.
 *
 * <p>In Spring Boot 4.x, {@code @WebMvcTest} no longer auto-configures Spring Security,
 * so {@code @AutoConfigureMockMvc(addFilters = false)} is used to disable all filters
 * (including security) for controller-level unit testing.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@WebMvcTest(TransferController.class)
@AutoConfigureMockMvc(addFilters = false)
class TransferControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private InitiateTransferUseCase initiateTransferUseCase;
    @MockitoBean private TransferQueryUseCase transferQueryUseCase;


    @Test
    @DisplayName("POST /api/v1/transfers → 201 CREATED")
    void shouldCreateTransfer() throws Exception {
        UUID src = UUID.randomUUID(), tgt = UUID.randomUUID(), tid = UUID.randomUUID();
        var resp = new TransferResponse(tid, "INITIATED", src, tgt,
                new BigDecimal("500.00"), "USD", null, "EUR", null, null, "k1");
        when(initiateTransferUseCase.handle(any())).thenReturn(resp);

        var req = new InitiateTransferRequest(src, tgt, new BigDecimal("500.00"),
                Currency.USD, Currency.EUR, "k1");

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(tid.toString()))
                .andExpect(jsonPath("$.status").value("INITIATED"));
    }

    @Test
    @DisplayName("GET /api/v1/transfers/{id} → 200 OK")
    void shouldGetTransfer() throws Exception {
        UUID id = UUID.randomUUID();
        var resp = new TransferResponse(id, "COMPLETED", UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100.00"), "USD", new BigDecimal("92.50"), "EUR",
                new BigDecimal("0.925"), null, "k2");
        when(transferQueryUseCase.findById(id)).thenReturn(resp);

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
}

