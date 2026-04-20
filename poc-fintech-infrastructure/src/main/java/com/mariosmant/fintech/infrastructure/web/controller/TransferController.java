package com.mariosmant.fintech.infrastructure.web.controller;

import com.mariosmant.fintech.application.command.InitiateTransferCommand;
import com.mariosmant.fintech.application.dto.TransferResponse;
import com.mariosmant.fintech.application.usecase.InitiateTransferUseCase;
import com.mariosmant.fintech.application.usecase.TransferQueryUseCase;
import com.mariosmant.fintech.infrastructure.security.SecurityContextUtil;
import com.mariosmant.fintech.infrastructure.security.audit.Audited;
import com.mariosmant.fintech.infrastructure.web.dto.InitiateTransferRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for money transfer operations.
 * All endpoints require OAuth2 authentication. Initiator is set from JWT.
 *
 * @author mariosmant
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers", description = "Money transfer operations — CQRS command & query endpoints")
@SecurityRequirement(name = "bearer-jwt")
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
     * Initiator is set to the authenticated user — never from client input.
     */
    @PostMapping
    @Audited(action = "INITIATE_TRANSFER", resourceType = "Transfer")
    @Operation(summary = "Initiate a money transfer",
            description = "Creates a new transfer. Uses idempotency key for exactly-once semantics. "
                    + "Initiator is set from authenticated user's JWT. "
                    + "Triggers the Saga orchestrator for multi-step processing.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transfer initiated successfully",
                    content = @Content(schema = @Schema(implementation = TransferResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error — invalid request body",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid JWT"),
            @ApiResponse(responseCode = "409", description = "Duplicate transfer — idempotency key already used",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Fraud detected — transaction rejected",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<TransferResponse> initiateTransfer(
            @Valid @RequestBody InitiateTransferRequest request) {
        // User ID from JWT — never trust client-supplied initiator (NIST IA-2)
        String userId = SecurityContextUtil.getAuthenticatedUserId();
        var command = new InitiateTransferCommand(
                request.sourceAccountId(),
                request.targetAccountId(),
                request.targetIban(),
                request.amount(),
                request.sourceCurrency(),
                request.targetCurrency(),
                request.idempotencyKey(),
                userId
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
            description = "Retrieves the current status and details of a transfer.")
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
            description = "Returns latest transfers ordered by most recent first.")
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

