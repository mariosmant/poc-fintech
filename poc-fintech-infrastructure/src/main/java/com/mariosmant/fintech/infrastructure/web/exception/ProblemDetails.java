package com.mariosmant.fintech.infrastructure.web.exception;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.time.Instant;

/**
 * RFC 7807 {@link ProblemDetail} factory with automatic enrichment.
 *
 * <p>Centralises the creation of error response bodies so every handler in
 * {@link GlobalExceptionHandler} (and any future {@code @RestControllerAdvice})
 * emits the same shape: fixed canonical {@code type} URIs, non-null
 * {@code title}, opaque {@code detail}, and a consistent set of extension
 * members useful to an incident responder without leaking implementation
 * detail:</p>
 * <ul>
 *   <li>{@code timestamp} — {@link Instant#now()} in ISO-8601.</li>
 *   <li>{@code requestId} — copied from MDC ({@code X-Request-ID} correlation
 *       populated by {@code MdcLoggingFilter}).</li>
 *   <li>{@code traceId} — copied from MDC (W3C Trace Context).</li>
 *   <li>{@code instance} — the current request URI, when running inside a
 *       Servlet request scope.</li>
 * </ul>
 *
 * <p>Intentionally <b>not</b> included: stack traces, exception class names,
 * internal IDs, or any message derived from untrusted input. Error messages
 * originating from {@link Exception#getMessage()} must be caller-safe before
 * being passed to {@link #of(HttpStatus, ErrorType, String, String)}.</p>
 *
 * <p>All fields are final; instances returned are freshly allocated per call
 * so callers may further decorate them (e.g. add per-handler
 * {@code pd.setProperty("errors", …)}).</p>
 *
 * @author mariosmant
 * @since 1.0.0
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7807">RFC 7807 — Problem Details for HTTP APIs</a>
 */
public final class ProblemDetails {

    private ProblemDetails() {
        // Utility class; not instantiable.
    }

    /** Canonical {@code type} URIs. Pinned values — do not mutate once released. */
    public enum ErrorType {
        DUPLICATE_TRANSFER   ("urn:fintech:error:duplicate-transfer",   "Duplicate Transfer"),
        INSUFFICIENT_FUNDS   ("urn:fintech:error:insufficient-funds",   "Insufficient Funds"),
        FRAUD_DETECTED       ("urn:fintech:error:fraud-detected",       "Fraud Detected"),
        INVALID_TRANSFER_STATE("urn:fintech:error:invalid-state",       "Invalid Transfer State"),
        NOT_FOUND            ("urn:fintech:error:not-found",            "Resource Not Found"),
        FORBIDDEN            ("urn:fintech:error:forbidden",            "Access Denied"),
        UNAUTHORIZED         ("urn:fintech:error:unauthorized",         "Unauthorized"),
        VALIDATION           ("urn:fintech:error:validation",           "Validation Error"),
        RATE_LIMITED         ("urn:fintech:error:rate-limit",           "Too Many Requests"),
        BLOCKED_BY_REPUTATION("urn:fintech:error:blocked-by-reputation", "Blocked by IP reputation"),
        INTERNAL             ("urn:fintech:error:internal",             "Internal Server Error");

        private final URI type;
        private final String defaultTitle;

        ErrorType(String type, String defaultTitle) {
            this.type = URI.create(type);
            this.defaultTitle = defaultTitle;
        }

        public URI type() { return type; }
        public String defaultTitle() { return defaultTitle; }
    }

    /**
     * Build a {@link ProblemDetail} with a canonical type URI, a caller-supplied
     * title (or the type's default), a caller-safe detail, and the standard
     * enrichment (timestamp, requestId, traceId, instance).
     *
     * @param status HTTP status; must be a client- or server-error code.
     * @param type   canonical error classification.
     * @param title  short human-readable summary; {@code null} falls back to
     *               {@link ErrorType#defaultTitle()}.
     * @param detail caller-safe explanation shown to the client. MUST NOT
     *               include stack traces, SQL, or untrusted input.
     * @return a new, fully enriched {@link ProblemDetail}.
     */
    public static ProblemDetail of(HttpStatus status, ErrorType type, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(type.type());
        pd.setTitle(title != null ? title : type.defaultTitle());
        enrich(pd);
        return pd;
    }

    /**
     * Convenience overload — uses the {@link ErrorType#defaultTitle()} and passes
     * {@code detail} through unchanged. Prefer this for straight-through domain
     * exception mapping; use {@link #of(HttpStatus, ErrorType, String, String)}
     * when you need to override the title or sanitise the detail further.
     */
    public static ProblemDetail of(HttpStatus status, ErrorType type, String detail) {
        return of(status, type, null, detail);
    }

    /**
     * Populate the standard extension members. Package-visible for tests.
     */
    static void enrich(ProblemDetail pd) {
        pd.setProperty("timestamp", Instant.now());

        String requestId = MDC.get("requestId");
        if (requestId != null && !requestId.isBlank()) {
            pd.setProperty("requestId", requestId);
        }
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            pd.setProperty("traceId", traceId);
        }

        URI instance = currentRequestUri();
        if (instance != null) {
            pd.setInstance(instance);
        }
    }

    /**
     * Extract the current request URI from {@link RequestContextHolder}, if a
     * Servlet request is bound to the current thread. Returns {@code null}
     * outside a request scope (e.g. scheduled tasks, Kafka listeners) so the
     * {@code instance} field is simply omitted.
     */
    private static URI currentRequestUri() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                String uri = sra.getRequest().getRequestURI();
                if (uri != null && !uri.isBlank()) {
                    return URI.create(uri);
                }
            }
        } catch (RuntimeException ignored) {
            // Defensive: never let URI building block an error response.
        }
        return null;
    }
}

