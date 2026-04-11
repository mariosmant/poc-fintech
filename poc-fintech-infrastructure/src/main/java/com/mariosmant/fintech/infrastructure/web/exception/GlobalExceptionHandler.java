package com.mariosmant.fintech.infrastructure.web.exception;

import com.mariosmant.fintech.domain.exception.DuplicateTransferException;
import com.mariosmant.fintech.domain.exception.FraudDetectedException;
import com.mariosmant.fintech.domain.exception.InsufficientFundsException;
import com.mariosmant.fintech.domain.exception.InvalidTransferStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

/**
 * Global exception handler — maps domain exceptions to RFC 7807 Problem Detail responses.
 *
 * <p>OWASP best practice: never expose internal stack traces or implementation
 * details in error responses. All error messages are sanitised.</p>
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
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Duplicate Transfer");
        pd.setType(URI.create("urn:fintech:error:duplicate-transfer"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ProblemDetail handleInsufficientFunds(InsufficientFundsException ex) {
        log.warn("Insufficient funds: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Insufficient Funds");
        pd.setType(URI.create("urn:fintech:error:insufficient-funds"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(FraudDetectedException.class)
    public ProblemDetail handleFraud(FraudDetectedException ex) {
        log.warn("Fraud detected: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, "Transaction rejected by fraud detection");
        pd.setTitle("Fraud Detected");
        pd.setType(URI.create("urn:fintech:error:fraud-detected"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(InvalidTransferStateException.class)
    public ProblemDetail handleInvalidState(InvalidTransferStateException ex) {
        log.error("Invalid transfer state: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Transfer is in an invalid state for this operation");
        pd.setTitle("Invalid Transfer State");
        pd.setType(URI.create("urn:fintech:error:invalid-state"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleNotFound(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Resource Not Found");
        pd.setType(URI.create("urn:fintech:error:not-found"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Validation failed");
        pd.setTitle("Validation Error");
        pd.setType(URI.create("urn:fintech:error:validation"));
        pd.setProperty("timestamp", Instant.now());
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        // Never expose internal details — OWASP Top 10 A09 Security Logging
        log.error("Unexpected error", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        pd.setTitle("Internal Server Error");
        pd.setType(URI.create("urn:fintech:error:internal"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}

