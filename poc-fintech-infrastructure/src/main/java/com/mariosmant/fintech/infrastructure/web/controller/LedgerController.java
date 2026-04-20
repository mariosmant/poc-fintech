package com.mariosmant.fintech.infrastructure.web.controller;

import com.mariosmant.fintech.application.dto.LedgerEntryResponse;
import com.mariosmant.fintech.application.usecase.LedgerQueryUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for double-entry ledger queries.
 * All endpoints require OAuth2 authentication.
 *
 * @author mariosmant
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/ledger")
@Tag(name = "Ledger", description = "Double-entry accounting ledger queries — immutable audit trail")
@SecurityRequirement(name = "bearer-jwt")
public class LedgerController {

    private final LedgerQueryUseCase ledgerQueryUseCase;

    public LedgerController(LedgerQueryUseCase ledgerQueryUseCase) {
        this.ledgerQueryUseCase = ledgerQueryUseCase;
    }

    /**
     * Retrieves all ledger entries for a specific account.
     *
     * @param accountId the account UUID
     * @return list of ledger entries (debits and credits)
     */
    @GetMapping("/account/{accountId}")
    @Operation(summary = "Get ledger entries by account ID",
            description = "Returns all debit and credit entries for the specified account, "
                    + "ordered chronologically. Supports audit and reconciliation.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ledger entries found",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = LedgerEntryResponse.class)))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<List<LedgerEntryResponse>> getByAccount(
            @Parameter(description = "UUID of the account", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID accountId) {
        return ResponseEntity.ok(ledgerQueryUseCase.findByAccountId(accountId));
    }

    /**
     * Retrieves all ledger entries for a specific transfer.
     *
     * @param transferId the transfer UUID
     * @return list of ledger entries (debit + credit pair for double-entry)
     */
    @GetMapping("/transfer/{transferId}")
    @Operation(summary = "Get ledger entries by transfer ID",
            description = "Returns the debit and credit entry pair for the specified transfer. "
                    + "A completed transfer always has exactly two entries (double-entry accounting).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ledger entries found",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = LedgerEntryResponse.class)))),
            @ApiResponse(responseCode = "404", description = "Transfer not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<List<LedgerEntryResponse>> getByTransfer(
            @Parameter(description = "UUID of the transfer", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID transferId) {
        return ResponseEntity.ok(ledgerQueryUseCase.findByTransferId(transferId));
    }

    /** Returns recent ledger entries for monitoring screens. */
    @GetMapping("/recent")
    @Operation(summary = "Get recent ledger entries",
            description = "Returns latest ledger entries ordered by creation time descending.")
    public ResponseEntity<List<LedgerEntryResponse>> getRecent(
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 500));
        return ResponseEntity.ok(ledgerQueryUseCase.findRecent(boundedLimit));
    }
}

