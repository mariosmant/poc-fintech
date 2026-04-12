package com.mariosmant.fintech.infrastructure.web.controller;

import com.mariosmant.fintech.application.command.InitiateTransferCommand;
import com.mariosmant.fintech.application.dto.TransferResponse;
import com.mariosmant.fintech.application.usecase.InitiateTransferUseCase;
import com.mariosmant.fintech.application.usecase.TransferQueryUseCase;
import com.mariosmant.fintech.infrastructure.web.dto.InitiateTransferRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for money transfer operations.
 *
 * <p>Exposes the CQRS write (POST) and read (GET) endpoints for transfers.
 * All endpoints are documented via OpenAPI 3.x annotations for Swagger UI.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers", description = "Money transfer operations — CQRS command & query endpoints")
public class TransferController {

    private final InitiateTransferUseCase initiateTransferUseCase;
    private final TransferQueryUseCase transferQueryUseCase;

    public TransferController(InitiateTransferUseCase initiateTransferUseCase,
                              TransferQueryUseCase transferQueryUseCase) {
        this.initiateTransferUseCase = initiateTransferUseCase;
        this.transferQueryUseCase = transferQueryUseCase;
    }

    /**
     * Initiates a new money transfer.
     *
     * @param request the transfer request body
     * @return the created transfer details
     */
    @PostMapping
    @Operation(summary = "Initiate a money transfer",
            description = "Creates a new transfer. Uses idempotency key for exactly-once semantics. "
                    + "Triggers the Saga orchestrator for multi-step processing including fraud check, "
                    + "FX conversion, and double-entry ledger posting.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transfer initiated successfully",
                    content = @Content(schema = @Schema(implementation = TransferResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error — invalid request body",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Duplicate transfer — idempotency key already used",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Fraud detected — transaction rejected",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<TransferResponse> initiateTransfer(
            @Valid @RequestBody InitiateTransferRequest request) {
        var command = new InitiateTransferCommand(
                request.sourceAccountId(),
                request.targetAccountId(),
                request.amount(),
                request.sourceCurrency(),
                request.targetCurrency(),
                request.idempotencyKey()
        );
        TransferResponse response = initiateTransferUseCase.handle(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves a transfer by its ID.
     *
     * @param id the transfer UUID
     * @return the transfer details
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get transfer by ID",
            description = "Retrieves the current status and details of a transfer, "
                    + "including FX conversion details and saga progression.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transfer found",
                    content = @Content(schema = @Schema(implementation = TransferResponse.class))),
            @ApiResponse(responseCode = "404", description = "Transfer not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<TransferResponse> getTransfer(
            @Parameter(description = "UUID of the transfer", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID id) {
        return ResponseEntity.ok(transferQueryUseCase.findById(id));
    }

    /** Returns latest transfers for monitoring pages. */
    @GetMapping
    @Operation(summary = "List latest transfers",
            description = "Returns latest transfers ordered by most recent first."
                    + " Intended for monitoring screens.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transfers retrieved",
                    content = @Content(schema = @Schema(implementation = TransferResponse.class)))
    })
    public ResponseEntity<List<TransferResponse>> listTransfers(
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 200));
        return ResponseEntity.ok(transferQueryUseCase.findLatest(boundedLimit));
    }
}

