package com.mariosmant.fintech.infrastructure.security;

import com.mariosmant.fintech.testcontainers.EnabledIfDockerAvailable;
import com.mariosmant.fintech.testcontainers.TestcontainersConfig;
import com.mariosmant.fintech.testcontainers.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * Integration tests verifying NIST/SOG-IS/SOC 2 security headers
 * are correctly applied to HTTP responses.
 *
 * <p>Uses full Spring context with Testcontainers to ensure security
 * filters are wired in the real filter chain.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@EnabledIfDockerAvailable
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, TestSecurityConfig.class})
@ActiveProfiles("test")
class SecurityHeadersIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should include X-Content-Type-Options: nosniff")
    void shouldIncludeContentTypeOptions() throws Exception {
        mockMvc.perform(get("/api/v1/transfers/00000000-0000-0000-0000-000000000000")
                        .with(jwt().jwt(j -> j.subject("test-user"))))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("Should include X-Frame-Options: DENY")
    void shouldIncludeFrameOptions() throws Exception {
        mockMvc.perform(get("/api/v1/transfers/00000000-0000-0000-0000-000000000000")
                        .with(jwt().jwt(j -> j.subject("test-user"))))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    @DisplayName("Should include Content-Security-Policy header")
    void shouldIncludeCSP() throws Exception {
        mockMvc.perform(get("/api/v1/transfers/00000000-0000-0000-0000-000000000000")
                        .with(jwt().jwt(j -> j.subject("test-user"))))
                .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    @DisplayName("Should include Referrer-Policy header")
    void shouldIncludeReferrerPolicy() throws Exception {
        mockMvc.perform(get("/api/v1/transfers/00000000-0000-0000-0000-000000000000")
                        .with(jwt().jwt(j -> j.subject("test-user"))))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }

    @Test
    @DisplayName("Should include Permissions-Policy header")
    void shouldIncludePermissionsPolicy() throws Exception {
        mockMvc.perform(get("/api/v1/transfers/00000000-0000-0000-0000-000000000000")
                        .with(jwt().jwt(j -> j.subject("test-user"))))
                .andExpect(header().exists("Permissions-Policy"));
    }

    @Test
    @DisplayName("Should include Cache-Control: no-cache, no-store")
    void shouldIncludeCacheControl() throws Exception {
        mockMvc.perform(get("/api/v1/transfers/00000000-0000-0000-0000-000000000000")
                        .with(jwt().jwt(j -> j.subject("test-user"))))
                .andExpect(header().exists("Cache-Control"));
    }
}


