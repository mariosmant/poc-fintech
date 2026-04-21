package com.mariosmant.fintech.infrastructure.web.exception;

import com.mariosmant.fintech.infrastructure.web.exception.ProblemDetails.ErrorType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ProblemDetails}.
 *
 * <p>Verifies the RFC 7807 shape, MDC correlation propagation, request-scope
 * {@code instance} URI extraction, and absence of enrichment when no request
 * is bound (Kafka listeners, scheduled tasks).</p>
 */
class ProblemDetailsTest {

    @BeforeEach
    void reset() {
        MDC.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    @DisplayName("ErrorType enum")
    class ErrorTypes {

        @Test
        @DisplayName("all type URIs use the urn:fintech:error: namespace")
        void allUseFintechNamespace() {
            for (ErrorType t : ErrorType.values()) {
                assertThat(t.type().toString()).startsWith("urn:fintech:error:");
                assertThat(t.defaultTitle()).isNotBlank();
            }
        }
    }

    @Nested
    @DisplayName("of()")
    class Factory {

        @Test
        @DisplayName("sets status, type, title, detail and timestamp")
        void buildsCanonicalShape() {
            ProblemDetail pd = ProblemDetails.of(
                    HttpStatus.CONFLICT,
                    ErrorType.DUPLICATE_TRANSFER,
                    "idempotency key reused");

            assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
            assertThat(pd.getType()).isEqualTo(URI.create("urn:fintech:error:duplicate-transfer"));
            assertThat(pd.getTitle()).isEqualTo("Duplicate Transfer");
            assertThat(pd.getDetail()).isEqualTo("idempotency key reused");
            assertThat(pd.getProperties()).containsKey("timestamp");
            assertThat(pd.getProperties().get("timestamp")).isInstanceOf(Instant.class);
        }

        @Test
        @DisplayName("honours explicit title override")
        void overridesTitle() {
            ProblemDetail pd = ProblemDetails.of(
                    HttpStatus.BAD_REQUEST, ErrorType.VALIDATION,
                    "Custom Title", "bad input");
            assertThat(pd.getTitle()).isEqualTo("Custom Title");
        }

        @Test
        @DisplayName("adds requestId and traceId from MDC when present")
        void copiesMdcCorrelation() {
            MDC.put("requestId", "req-42");
            MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");

            ProblemDetail pd = ProblemDetails.of(
                    HttpStatus.INTERNAL_SERVER_ERROR, ErrorType.INTERNAL, "boom");

            assertThat(pd.getProperties())
                    .containsEntry("requestId", "req-42")
                    .containsEntry("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
        }

        @Test
        @DisplayName("omits correlation fields when MDC empty")
        void omitsAbsentCorrelation() {
            ProblemDetail pd = ProblemDetails.of(
                    HttpStatus.NOT_FOUND, ErrorType.NOT_FOUND, "nope");
            assertThat(pd.getProperties()).doesNotContainKeys("requestId", "traceId");
        }

        @Test
        @DisplayName("omits correlation fields for blank MDC values")
        void omitsBlankMdc() {
            MDC.put("requestId", "  ");
            MDC.put("traceId", "");
            ProblemDetail pd = ProblemDetails.of(
                    HttpStatus.NOT_FOUND, ErrorType.NOT_FOUND, "nope");
            assertThat(pd.getProperties()).doesNotContainKeys("requestId", "traceId");
        }

        @Test
        @DisplayName("sets instance URI when a Servlet request is bound")
        void setsInstanceFromRequest() {
            var req = new MockHttpServletRequest("GET", "/api/v1/accounts/abc");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

            ProblemDetail pd = ProblemDetails.of(
                    HttpStatus.NOT_FOUND, ErrorType.NOT_FOUND, "gone");

            assertThat(pd.getInstance()).isEqualTo(URI.create("/api/v1/accounts/abc"));
        }

        @Test
        @DisplayName("leaves instance unset outside a request scope (Kafka / scheduled)")
        void omitsInstanceOutsideRequest() {
            ProblemDetail pd = ProblemDetails.of(
                    HttpStatus.INTERNAL_SERVER_ERROR, ErrorType.INTERNAL, "boom");
            assertThat(pd.getInstance()).isNull();
        }
    }
}

