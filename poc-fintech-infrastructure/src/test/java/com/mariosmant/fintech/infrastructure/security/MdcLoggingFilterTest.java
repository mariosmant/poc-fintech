package com.mariosmant.fintech.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MdcLoggingFilter}.
 *
 * <p>Splits across three concerns:</p>
 * <ul>
 *   <li>Request-id sanitisation (log-injection defence)</li>
 *   <li>W3C {@code traceparent} parsing</li>
 *   <li>End-to-end MDC population during a filter invocation, including the
 *       JWT-derived {@code userId} / {@code username}</li>
 * </ul>
 */
class MdcLoggingFilterTest {

    private final MdcLoggingFilter filter = new MdcLoggingFilter();

    @BeforeEach
    void clearMdc() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("sanitiseRequestId()")
    class SanitiseRequestId {

        @Test
        @DisplayName("accepts safe alphanumeric + [._:-] up to 128 chars")
        void acceptsSafeId() {
            assertThat(MdcLoggingFilter.sanitiseRequestId("req-123.abc:xyz_01"))
                    .isEqualTo("req-123.abc:xyz_01");
        }

        @Test
        @DisplayName("generates UUIDv4 on null header")
        void generatesOnNull() {
            String id = MdcLoggingFilter.sanitiseRequestId(null);
            assertThat(UUID.fromString(id)).isNotNull(); // throws if not a UUID
        }

        @Test
        @DisplayName("rejects CR/LF injection and regenerates")
        void rejectsLogInjection() {
            String id = MdcLoggingFilter.sanitiseRequestId("abc\r\nSET-COOKIE: x=y");
            assertThat(id).doesNotContain("\n").doesNotContain("\r");
            assertThat(UUID.fromString(id)).isNotNull();
        }

        @Test
        @DisplayName("rejects overly long values (>128)")
        void rejectsOverlyLong() {
            String id = MdcLoggingFilter.sanitiseRequestId("a".repeat(129));
            assertThat(id).hasSize(36); // UUID fallback
        }

        @Test
        @DisplayName("rejects whitespace-only header values")
        void rejectsBlank() {
            String id = MdcLoggingFilter.sanitiseRequestId("   ");
            assertThat(id).hasSize(36);
        }
    }

    @Nested
    @DisplayName("populateTraceContext() — W3C traceparent")
    class TraceContext {

        @Test
        @DisplayName("extracts trace-id and span-id from valid traceparent")
        void extractsValid() {
            MdcLoggingFilter.populateTraceContext(
                    "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
            assertThat(MDC.get(MdcLoggingFilter.MDC_TRACE_ID))
                    .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
            assertThat(MDC.get(MdcLoggingFilter.MDC_SPAN_ID))
                    .isEqualTo("00f067aa0ba902b7");
        }

        @Test
        @DisplayName("normalises mixed-case hex to lowercase")
        void normalisesCase() {
            MdcLoggingFilter.populateTraceContext(
                    "00-4BF92F3577B34DA6A3CE929D0E0E4736-00F067AA0BA902B7-01");
            assertThat(MDC.get(MdcLoggingFilter.MDC_TRACE_ID))
                    .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        }

        @Test
        @DisplayName("ignores malformed traceparent (wrong segment count)")
        void ignoresMalformed() {
            MdcLoggingFilter.populateTraceContext("not-a-valid-traceparent");
            assertThat(MDC.get(MdcLoggingFilter.MDC_TRACE_ID)).isNull();
            assertThat(MDC.get(MdcLoggingFilter.MDC_SPAN_ID)).isNull();
        }

        @Test
        @DisplayName("ignores traceparent with wrong hex lengths")
        void ignoresWrongLength() {
            // trace-id too short
            MdcLoggingFilter.populateTraceContext("00-abcd-00f067aa0ba902b7-01");
            assertThat(MDC.get(MdcLoggingFilter.MDC_TRACE_ID)).isNull();
        }

        @Test
        @DisplayName("no-ops on null/blank input")
        void ignoresBlank() {
            MdcLoggingFilter.populateTraceContext(null);
            MdcLoggingFilter.populateTraceContext("   ");
            assertThat(MDC.get(MdcLoggingFilter.MDC_TRACE_ID)).isNull();
        }

        @Test
        @DisplayName("does NOT overwrite MDC values pre-populated by Micrometer Tracing")
        void respectsExistingMdc() {
            MDC.put(MdcLoggingFilter.MDC_TRACE_ID, "micrometer-set-value");
            MdcLoggingFilter.populateTraceContext(
                    "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
            assertThat(MDC.get(MdcLoggingFilter.MDC_TRACE_ID)).isEqualTo("micrometer-set-value");
        }
    }

    @Nested
    @DisplayName("doFilterInternal() end-to-end")
    class EndToEnd {

        @Test
        @DisplayName("populates MDC during chain and clears it after")
        void populatesAndClears() throws Exception {
            var request = new MockHttpServletRequest("GET", "/api/v1/accounts");
            request.addHeader(MdcLoggingFilter.HEADER_REQUEST_ID, "req-001");
            request.addHeader(MdcLoggingFilter.HEADER_TRACEPARENT,
                    "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
            var response = new MockHttpServletResponse();

            // Seed an authenticated JWT so userId/username get populated.
            Jwt jwt = Jwt.withTokenValue("t")
                    .header("alg", "RS256")
                    .subject("user-abc")
                    .claim("preferred_username", "alice")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();
            SecurityContextHolder.getContext().setAuthentication(
                    new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

            var captured = new AtomicReference<java.util.Map<String, String>>();
            FilterChain chain = (req, res) -> captured.set(MDC.getCopyOfContextMap());

            filter.doFilter(request, response, chain);

            // Captured mid-chain
            assertThat(captured.get())
                    .containsEntry(MdcLoggingFilter.MDC_REQUEST_ID, "req-001")
                    .containsEntry(MdcLoggingFilter.MDC_TRACE_ID, "4bf92f3577b34da6a3ce929d0e0e4736")
                    .containsEntry(MdcLoggingFilter.MDC_SPAN_ID, "00f067aa0ba902b7")
                    .containsEntry(MdcLoggingFilter.MDC_USER_ID, "user-abc")
                    .containsEntry(MdcLoggingFilter.MDC_USERNAME, "alice");

            // Response header mirrored
            assertThat(response.getHeader(MdcLoggingFilter.HEADER_REQUEST_ID)).isEqualTo("req-001");

            // MDC cleared after the filter returns — no cross-request leakage
            assertThat(MDC.get(MdcLoggingFilter.MDC_REQUEST_ID)).isNull();
            assertThat(MDC.get(MdcLoggingFilter.MDC_USER_ID)).isNull();
        }

        @Test
        @DisplayName("clears MDC even when downstream chain throws")
        void clearsMdcOnException() {
            var request = new MockHttpServletRequest("GET", "/api/v1/accounts");
            var response = new MockHttpServletResponse();
            FilterChain boom = (req, res) -> { throw new RuntimeException("boom"); };

            try {
                filter.doFilter(request, response, boom);
            } catch (Exception ignored) { /* expected */ }

            assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        }

        @Test
        @DisplayName("generates a UUID requestId when no header is supplied")
        void generatesRequestId() throws Exception {
            HttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
            HttpServletResponse response = new MockHttpServletResponse();

            var captured = new AtomicReference<String>();
            FilterChain chain = (req, res) -> captured.set(MDC.get(MdcLoggingFilter.MDC_REQUEST_ID));

            filter.doFilter(request, response, chain);

            assertThat(captured.get()).isNotBlank();
            assertThat(UUID.fromString(captured.get())).isNotNull();
        }
    }
}



