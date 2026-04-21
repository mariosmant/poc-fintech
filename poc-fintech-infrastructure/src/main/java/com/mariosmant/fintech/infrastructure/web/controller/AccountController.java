package com.mariosmant.fintech.infrastructure.web.controller;

import com.mariosmant.fintech.application.command.CreateAccountCommand;
import com.mariosmant.fintech.application.dto.AccountResponse;
import com.mariosmant.fintech.application.usecase.AccountUseCase;
import com.mariosmant.fintech.infrastructure.security.SecurityContextUtil;
import com.mariosmant.fintech.infrastructure.security.audit.Audited;
import com.mariosmant.fintech.infrastructure.web.dto.CreateAccountRequest;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for account operations.
 * All endpoints require OAuth2 authentication. User ID is extracted from JWT.
 *
 * @author mariosmant
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts", description = "Account management operations — create and query financial accounts")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('USER')")
public class AccountController {

    private final AccountUseCase accountUseCase;

    public AccountController(AccountUseCase accountUseCase) {
        this.accountUseCase = accountUseCase;
    }

    /**
     * Creates a new financial account.
     * Owner is set to the authenticated user — never from client input.
     */
    @PostMapping
    @Audited(action = "CREATE_ACCOUNT", resourceType = "Account")
    @Operation(summary = "Create a new account",
            description = "Creates a new financial account. Owner is set to the authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created successfully",
                    content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error — invalid request body",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid JWT")
    })
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {
        // User ID and username from JWT — never trust client-supplied identity (NIST IA-2)
        String userId = SecurityContextUtil.getAuthenticatedUserId();
        String ownerName = SecurityContextUtil.getAuthenticatedUsername();
        var command = new CreateAccountCommand(
                ownerName, request.currency(), request.initialBalance(), userId);
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

    /** Returns accounts: all for admins, only own-owned for regular users. */
    @GetMapping
    @Operation(summary = "List accounts",
            description = "Returns all accounts owned by the authenticated user. "
                    + "Admins receive every account in the system (for reconciliation / support).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Accounts retrieved",
                    content = @Content(schema = @Schema(implementation = AccountResponse.class)))
    })
    public ResponseEntity<List<AccountResponse>> listAccounts() {
        if (SecurityContextUtil.isAdmin()) {
            return ResponseEntity.ok(accountUseCase.findAll());
        }
        String userId = SecurityContextUtil.getAuthenticatedUserId();
        return ResponseEntity.ok(accountUseCase.findByOwnerId(userId));
    }
}

