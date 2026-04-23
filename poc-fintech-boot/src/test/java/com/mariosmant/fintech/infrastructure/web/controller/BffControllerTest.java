package com.mariosmant.fintech.infrastructure.web.controller;

import com.mariosmant.fintech.testcontainers.EnabledIfDockerAvailable;
import com.mariosmant.fintech.testcontainers.TestSecurityConfig;
import com.mariosmant.fintech.testcontainers.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice-level tests for {@link BffController}.
 *
 * <p>Covers the identity projection ({@code /bff/user}) and server-side logout
 * ({@code /bff/logout}) contracts required by the React BFF client
 * ({@code src/api/bffClient.ts}) and ADR-0010.</p>
 *
 * <p>Runs under the {@code test} profile with {@link TestSecurityConfig}, whose
 * mock {@code JwtDecoder} emits a token carrying
 * {@code preferred_username=test-user} and {@code realm_access.roles=[user]}.
 * This is deliberately the same identity wiring used by the rest of the
 * integration suite so {@code @PreAuthorize} behaves identically.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@EnabledIfDockerAvailable
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, TestSecurityConfig.class})
@ActiveProfiles("test")
class BffControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /bff/user returns the principal projection when authenticated")
    void userEndpointReturnsProjectionWhenAuthenticated() throws Exception {
        mockMvc.perform(get("/bff/user")
                        .with(jwt().jwt(j -> j
                                .subject("test-user-fixed-id")
                                .claim("preferred_username", "alice")
                                .claim("name", "Alice Anderson")
                                .claim("email", "alice@example.com")
                                .claim("realm_access", java.util.Map.of(
                                        "roles", java.util.List.of("user")))
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.subject").value("test-user-fixed-id"))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.name").value("Alice Anderson"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.hasItem("USER")))
                .andExpect(jsonPath("$.admin").value(false));
    }

    @Test
    @DisplayName("GET /bff/user sets admin=true when ROLE_ADMIN is granted")
    void userEndpointFlagsAdmin() throws Exception {
        mockMvc.perform(get("/bff/user")
                        .with(jwt().jwt(j -> j
                                .subject("admin-uid")
                                .claim("preferred_username", "admin")
                                .claim("realm_access", java.util.Map.of(
                                        "roles", java.util.List.of("user", "admin")))
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(true))
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.hasItems("USER", "ADMIN")));
    }

    @Test
    @DisplayName("POST /bff/logout clears __Host-SESSION and __Host-XSRF-TOKEN cookies and returns 204")
    void logoutClearsHostCookies() throws Exception {
        mockMvc.perform(post("/bff/logout")
                        .with(jwt().jwt(j -> j.subject("any"))))
                .andExpect(status().isNoContent())
                // Max-Age=0 == delete cookie; both __Host- cookies are included.
                .andExpect(result -> {
                    String setCookieHeaders = String.join(",", result.getResponse().getHeaders("Set-Cookie"));
                    org.hamcrest.MatcherAssert.assertThat(setCookieHeaders,
                            org.hamcrest.Matchers.containsString("__Host-SESSION="));
                    org.hamcrest.MatcherAssert.assertThat(setCookieHeaders,
                            org.hamcrest.Matchers.containsString("__Host-XSRF-TOKEN="));
                    org.hamcrest.MatcherAssert.assertThat(setCookieHeaders,
                            org.hamcrest.Matchers.containsString("Max-Age=0"));
                    org.hamcrest.MatcherAssert.assertThat(setCookieHeaders,
                            org.hamcrest.Matchers.containsString("Secure"));
                    org.hamcrest.MatcherAssert.assertThat(setCookieHeaders,
                            org.hamcrest.Matchers.containsString("HttpOnly"));
                    org.hamcrest.MatcherAssert.assertThat(setCookieHeaders,
                            org.hamcrest.Matchers.containsString("Path=/"));
                });
    }

    @Test
    @DisplayName("GET /bff/public/csrf is reachable without authentication")
    void csrfEndpointIsPublic() throws Exception {
        // Under the test filter chain all requests are permitted; this verifies the
        // endpoint is wired and responds 200 without an Authorization header.
        mockMvc.perform(get("/bff/public/csrf"))
                .andExpect(status().isOk());
    }
}

