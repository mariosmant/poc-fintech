package com.mariosmant.fintech.infrastructure.web.controller;

import com.mariosmant.fintech.application.command.CreateAccountCommand;
import com.mariosmant.fintech.application.dto.AccountResponse;
import com.mariosmant.fintech.application.usecase.AccountUseCase;
import com.mariosmant.fintech.infrastructure.web.dto.CreateAccountRequest;
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

import java.util.UUID;

/**
 * REST controller for account operations.
 *
 * <p>Provides endpoints for creating and retrieving financial accounts.
 * All endpoints are documented via OpenAPI 3.x annotations for Swagger UI.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts", description = "Account management operations — create and query financial accounts")
public class AccountController {

    private final AccountUseCase accountUseCase;

    public AccountController(AccountUseCase accountUseCase) {
        this.accountUseCase = accountUseCase;
    }

    /**
     * Creates a new financial account.
     *
     * @param request the account creation request
     * @return the created account details
     */
    @PostMapping
    @Operation(summary = "Create a new account",
            description = "Creates a new financial account with the specified owner, currency, and initial balance.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created successfully",
                    content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error — invalid request body",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {
        var command = new CreateAccountCommand(
                request.ownerName(), request.currency(), request.initialBalance());
        return ResponseEntity.status(HttpStatus.CREATED).body(accountUseCase.create(command));
    }

    /**
     * Retrieves an account by its ID.
     *
     * @param id the account UUID
     * @return the account details
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get account by ID",
            description = "Retrieves the current balance and details of a financial account.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account found",
                    content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<AccountResponse> getAccount(
            @Parameter(description = "UUID of the account", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID id) {
        return ResponseEntity.ok(accountUseCase.findById(id));
    }
}

