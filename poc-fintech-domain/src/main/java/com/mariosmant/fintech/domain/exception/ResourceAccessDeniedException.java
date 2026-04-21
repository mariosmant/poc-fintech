package com.mariosmant.fintech.domain.exception;

/**
 * Thrown by query use cases when the authenticated caller is not entitled to
 * view the requested resource — typically because they neither own the target
 * account nor hold an administrative role.
 *
 * <p>Framework-agnostic by design: the domain layer must not depend on Spring
 * Security. The infrastructure web layer ({@code GlobalExceptionHandler}) maps
 * this exception to an RFC 7807 {@code 403 Forbidden} response.</p>
 *
 * <p>This is distinct from Spring Security's {@code AccessDeniedException} —
 * the latter is raised by {@code @PreAuthorize} / filter chains for
 * URL/method-level gates, while this one is raised by domain-aware
 * row-level authorization checks (NIST AC-3, AC-6 — least privilege).</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public class ResourceAccessDeniedException extends RuntimeException {

    public ResourceAccessDeniedException(String message) {
        super(message);
    }
}

