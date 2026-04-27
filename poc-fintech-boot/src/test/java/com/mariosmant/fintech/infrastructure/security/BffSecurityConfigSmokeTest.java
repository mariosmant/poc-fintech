package com.mariosmant.fintech.infrastructure.security;

import com.mariosmant.fintech.testcontainers.EnabledIfDockerAvailable;
import com.mariosmant.fintech.testcontainers.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke test for the BFF security filter chain
 * ({@link BffSecurityConfig}).
 *
 * <p>Exercises the end-to-end wiring under the {@code bff} Spring profile:
 * {@code spring-boot-starter-oauth2-client}, session-backed OAuth2 Login,
 * CSRF double-submit via {@code __Host-XSRF-TOKEN}, and the global
 * {@code HttpStatusEntryPoint} that returns HTTP 401 (instead of a 302 to
 * Keycloak) for unauthenticated API/BFF calls.</p>
 *
 * <p>The test supplies its own {@link ClientRegistrationRepository} via
 * {@code @TestConfiguration} so Spring Boot's OAuth2 Client auto-configuration
 * ({@code @ConditionalOnMissingBean}) backs off without fetching OIDC
 * discovery from a live Keycloak.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@EnabledIfDockerAvailable
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, BffSecurityConfigSmokeTest.StaticClientRegistration.class})
@ActiveProfiles({"bff"})
class BffSecurityConfigSmokeTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    BffSecurityConfig bffSecurityConfig;

    @Test
    @DisplayName("BFF profile wires the OAuth2-Login filter chain (BffSecurityConfig bean present)")
    void bffChainWired() {
        assertThat(bffSecurityConfig).isNotNull();
    }

    @Test
    @DisplayName("Unauthenticated /bff/user returns 401 (not a 302 to Keycloak) — fetch-friendly entry point")
    void unauthenticatedBffUserReturns401() throws Exception {
        mockMvc.perform(get("/bff/user"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated /api/** returns 401 (not a 302) so the SPA can handle it without opaque-redirect")
    void unauthenticatedApiReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Hardened headers (COOP/COEP/CORP, CSP) are emitted in BFF mode — parity")
    void hardenedHeadersPresent() throws Exception {
        // Note: HSTS is only emitted over HTTPS; MockMvc uses http:// by default,
        // so Strict-Transport-Security is asserted in SecurityHeadersIntegrationTest
        // (real TLS), not here.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cross-Origin-Opener-Policy", "same-origin"))
                .andExpect(header().string("Cross-Origin-Embedder-Policy", "require-corp"))
                .andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().exists("Permissions-Policy"));
    }

    @Test
    @DisplayName("/oauth2/authorization/keycloak is reachable unauthenticated (starts the code flow)")
    void oauth2AuthorizationEndpointIsReachable() throws Exception {
        // Spring issues a 302 to Keycloak's authorization-uri; we only assert
        // the status — we do NOT follow the redirect.
        mockMvc.perform(get("/oauth2/authorization/keycloak"))
                .andExpect(status().is3xxRedirection());
    }

    /**
     * Supplies a fully-static ClientRegistrationRepository so the Spring Boot
     * OAuth2 Client auto-configuration (which would otherwise attempt OIDC
     * discovery against the YAML-declared issuer-uri at startup) backs off
     * via {@code @ConditionalOnMissingBean}.
     */
    @TestConfiguration
    static class StaticClientRegistration {
        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration keycloak = ClientRegistration.withRegistrationId("keycloak")
                    .clientId("poc-fintech-bff-server")
                    .clientSecret("test-secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "profile", "email")
                    .authorizationUri("http://localhost:8180/realms/fintech/protocol/openid-connect/auth")
                    .tokenUri("http://localhost:8180/realms/fintech/protocol/openid-connect/token")
                    .jwkSetUri("http://localhost:8180/realms/fintech/protocol/openid-connect/certs")
                    .userInfoUri("http://localhost:8180/realms/fintech/protocol/openid-connect/userinfo")
                    .userNameAttributeName("preferred_username")
                    .issuerUri("http://localhost:8180/realms/fintech")
                    .clientName("keycloak")
                    .build();
            return new InMemoryClientRegistrationRepository(keycloak);
        }
    }
}


