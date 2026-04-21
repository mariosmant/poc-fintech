package com.mariosmant.fintech.infrastructure.web.exception;

import com.mariosmant.fintech.domain.exception.DuplicateTransferException;
import com.mariosmant.fintech.domain.exception.FraudDetectedException;
import com.mariosmant.fintech.domain.exception.InsufficientFundsException;
import com.mariosmant.fintech.domain.exception.InvalidTransferStateException;
import com.mariosmant.fintech.infrastructure.web.exception.ProblemDetails.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler — maps domain and framework exceptions to RFC 7807
 * {@link ProblemDetail} responses via {@link ProblemDetails}.
 *
 * <p>OWASP A09: never expose stack traces, exception class names, or internal
 * identifiers. Each handler explicitly chooses the detail string passed to the
 * client — either a safe constant or {@code ex.getMessage()} for domain
 * exceptions whose messages we control.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DuplicateTransferException.class)
    public ProblemDetail handleDuplicate(DuplicateTransferException ex) {
        log.warn("Duplicate transfer: {}", ex.getMessage());
        return ProblemDetails.of(HttpStatus.CONFLICT, ErrorType.DUPLICATE_TRANSFER, ex.getMessage());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ProblemDetail handleInsufficientFunds(InsufficientFundsException ex) {
        log.warn("Insufficient funds: {}", ex.getMessage());
        return ProblemDetails.of(HttpStatus.BAD_REQUEST, ErrorType.INSUFFICIENT_FUNDS, ex.getMessage());
    }

    @ExceptionHandler(FraudDetectedException.class)
    public ProblemDetail handleFraud(FraudDetectedException ex) {
        log.warn("Fraud detected: {}", ex.getMessage());
        // Never echo fraud signals back to the caller — opaque detail only.
        return ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, ErrorType.FRAUD_DETECTED,
                "Transaction rejected by fraud detection");
    }

    @ExceptionHandler(InvalidTransferStateException.class)
    public ProblemDetail handleInvalidState(InvalidTransferStateException ex) {
        log.error("Invalid transfer state: {}", ex.getMessage());
        return ProblemDetails.of(HttpStatus.CONFLICT, ErrorType.INVALID_TRANSFER_STATE,
                "Transfer is in an invalid state for this operation");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleNotFound(IllegalArgumentException ex) {
        return ProblemDetails.of(HttpStatus.NOT_FOUND, ErrorType.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    public ProblemDetail handleForbidden(SecurityException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ProblemDetails.of(HttpStatus.FORBIDDEN, ErrorType.FORBIDDEN,
                "You do not have permission to access this resource");
    }

    /**
     * Handles Spring Security {@link AccessDeniedException} — thrown by the
     * method-security interceptor when {@code @PreAuthorize} evaluates to
     * {@code false}. Also covers {@code AuthorizationDeniedException}.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        log.warn("Authorization denied: {}", ex.getMessage());
        return ProblemDetails.of(HttpStatus.FORBIDDEN, ErrorType.FORBIDDEN,
                "You do not have permission to access this resource");
    }

    /**
     * Handles domain-layer {@link com.mariosmant.fintech.domain.exception.ResourceAccessDeniedException}
     * — raised by query use cases when the caller is authenticated but is not
     * entitled to see the specific row they requested (row-level authorization,
     * independent of method-level {@code @PreAuthorize}).
     */
    @ExceptionHandler(com.mariosmant.fintech.domain.exception.ResourceAccessDeniedException.class)
    public ProblemDetail handleResourceAccessDenied(
            com.mariosmant.fintech.domain.exception.ResourceAccessDeniedException ex) {
        log.warn("Row-level authorization denied: {}", ex.getMessage());
        return ProblemDetails.of(HttpStatus.FORBIDDEN, ErrorType.FORBIDDEN,
                "You do not have permission to access this resource");
    }

    /**
     * Handles {@link AuthenticationException} — thrown when method security is
     * invoked without any {@code Authentication} in the {@code SecurityContext}.
     * Returns 401 per RFC 7235.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleUnauthenticated(AuthenticationException ex) {
        log.warn("Authentication required: {}", ex.getMessage());
        return ProblemDetails.of(HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED,
                "Authentication is required to access this resource");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail pd = ProblemDetails.of(HttpStatus.BAD_REQUEST, ErrorType.VALIDATION,
                "Validation failed");
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        // OWASP A09: never expose internal details. Full exception goes to logs only.
        log.error("Unexpected error", ex);
        return ProblemDetails.of(HttpStatus.INTERNAL_SERVER_ERROR, ErrorType.INTERNAL,
                "An unexpected error occurred");
    }
}
