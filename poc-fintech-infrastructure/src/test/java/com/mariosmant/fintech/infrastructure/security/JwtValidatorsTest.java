package com.mariosmant.fintech.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtValidators}.
 *
 * <p>Located in the same package as the class under test so the package-private
 * per-validator helpers ({@code audience}, {@code tokenType}, {@code requiredClaims},
 * {@code authorizedParty}) can be exercised in isolation. The public
 * {@link JwtValidators#strict} composite is covered by a happy path + one-failure-per-dimension
 * sweep.</p>
 */
class JwtValidatorsTest {

    private static final String ISSUER = "http://localhost:18180/realms/fintech";
    private static final String AUDIENCE = "poc-fintech-api";
    private static final String AZP = "poc-fintech-spa";

    private static Jwt.Builder baseToken() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .header("typ", "JWT")
                .issuer(ISSUER)
                .subject("user-123")
                .audience(List.of(AUDIENCE))
                .issuedAt(now.minusSeconds(60))
                .expiresAt(now.plusSeconds(3600))
                .claim("azp", AZP);
    }

    @Nested
    @DisplayName("audience()")
    class AudienceTests {

        @Test
        void accepts_when_expected_audience_present_in_list() {
            OAuth2TokenValidator<Jwt> v = JwtValidators.audience(List.of(AUDIENCE));
            assertThat(v.validate(baseToken().build()).hasErrors()).isFalse();
        }

        @Test
        void accepts_string_form_aud() {
            OAuth2TokenValidator<Jwt> v = JwtValidators.audience(List.of(AUDIENCE));
            Map<String, Object> claims = new HashMap<>();
            claims.put("sub", "u");
            claims.put("iss", ISSUER);
            claims.put("aud", AUDIENCE);
            Instant now = Instant.now();
            Jwt jwt = new Jwt("t", now.minusSeconds(10), now.plusSeconds(10),
                    Map.of("alg", "RS256"), claims);
            assertThat(v.validate(jwt).hasErrors()).isFalse();
        }

        @Test
        void rejects_when_audience_missing() {
            OAuth2TokenValidator<Jwt> v = JwtValidators.audience(List.of(AUDIENCE));
            Map<String, Object> claims = new HashMap<>();
            claims.put("sub", "u");
            Instant now = Instant.now();
            Jwt jwt = new Jwt("t", now.minusSeconds(10), now.plusSeconds(10),
                    Map.of("alg", "RS256"), claims);
            OAuth2TokenValidatorResult r = v.validate(jwt);
            assertThat(r.hasErrors()).isTrue();
            assertThat(r.getErrors()).anySatisfy(e ->
                    assertThat(e.getDescription()).contains("aud"));
        }

        @Test
        void rejects_when_audience_mismatches() {
            OAuth2TokenValidator<Jwt> v = JwtValidators.audience(List.of(AUDIENCE));
            Jwt jwt = baseToken().audience(List.of("some-other-api")).build();
            assertThat(v.validate(jwt).hasErrors()).isTrue();
        }
    }

    @Nested
    @DisplayName("tokenType()")
    class TokenTypeTests {

        @Test
        void accepts_typ_JWT() {
            assertThat(JwtValidators.tokenType().validate(baseToken().build()).hasErrors()).isFalse();
        }

        @Test
        void accepts_typ_at_plus_jwt() {
            Jwt jwt = baseToken().header("typ", "at+jwt").build();
            assertThat(JwtValidators.tokenType().validate(jwt).hasErrors()).isFalse();
        }

        @Test
        void accepts_missing_typ() {
            Instant now = Instant.now();
            Jwt jwt = new Jwt("t", now.minusSeconds(10), now.plusSeconds(10),
                    Map.of("alg", "RS256"),
                    Map.of("sub", "u", "aud", List.of(AUDIENCE)));
            assertThat(JwtValidators.tokenType().validate(jwt).hasErrors()).isFalse();
        }

        @Test
        void rejects_unknown_typ() {
            Jwt jwt = baseToken().header("typ", "evil").build();
            OAuth2TokenValidatorResult r = JwtValidators.tokenType().validate(jwt);
            assertThat(r.hasErrors()).isTrue();
            assertThat(r.getErrors()).anySatisfy(e ->
                    assertThat(e.getDescription()).contains("typ"));
        }
    }

    @Nested
    @DisplayName("requiredClaims()")
    class RequiredClaimsTests {

        @Test
        void accepts_when_all_claims_present() {
            OAuth2TokenValidator<Jwt> v = JwtValidators.requiredClaims(List.of("sub", "iat", "exp"));
            assertThat(v.validate(baseToken().build()).hasErrors()).isFalse();
        }

        @Test
        void rejects_when_required_claim_missing() {
            OAuth2TokenValidator<Jwt> v = JwtValidators.requiredClaims(List.of("sub", "iat", "exp", "tenant"));
            OAuth2TokenValidatorResult r = v.validate(baseToken().build());
            assertThat(r.hasErrors()).isTrue();
            assertThat(r.getErrors()).anySatisfy(e ->
                    assertThat(e.getDescription()).contains("tenant"));
        }

        @Test
        void rejects_when_string_claim_is_blank() {
            OAuth2TokenValidator<Jwt> v = JwtValidators.requiredClaims(List.of("sub"));
            Jwt jwt = baseToken().subject("   ").build();
            assertThat(v.validate(jwt).hasErrors()).isTrue();
        }
    }

    @Nested
    @DisplayName("authorizedParty()")
    class AuthorizedPartyTests {

        @Test
        void skips_when_expected_is_blank() {
            OAuth2TokenValidator<Jwt> v = JwtValidators.authorizedParty("");
            assertThat(v.validate(baseToken().build()).hasErrors()).isFalse();
        }

        @Test
        void accepts_matching_azp() {
            OAuth2TokenValidator<Jwt> v = JwtValidators.authorizedParty(AZP);
            assertThat(v.validate(baseToken().build()).hasErrors()).isFalse();
        }

        @Test
        void accepts_missing_azp_per_spec() {
            OAuth2TokenValidator<Jwt> v = JwtValidators.authorizedParty(AZP);
            Jwt jwt = baseToken().claim("azp", null).build();
            assertThat(v.validate(jwt).hasErrors()).isFalse();
        }

        @Test
        void rejects_mismatching_azp() {
            OAuth2TokenValidator<Jwt> v = JwtValidators.authorizedParty(AZP);
            Jwt jwt = baseToken().claim("azp", "evil-client").build();
            OAuth2TokenValidatorResult r = v.validate(jwt);
            assertThat(r.hasErrors()).isTrue();
            assertThat(r.getErrors()).anySatisfy(e ->
                    assertThat(e.getDescription()).contains("azp"));
        }
    }

    @Nested
    @DisplayName("strict()")
    class StrictCompositeTests {

        @Test
        void happy_path_all_checks_pass() {
            OAuth2TokenValidator<Jwt> v = JwtValidators.strict(
                    ISSUER, List.of(AUDIENCE), null, null, AZP);
            assertThat(v.validate(baseToken().build()).hasErrors()).isFalse();
        }

        @Test
        void rejects_wrong_issuer() {
            OAuth2TokenValidator<Jwt> v = JwtValidators.strict(
                    ISSUER, List.of(AUDIENCE), null, null, AZP);
            Jwt jwt = baseToken().issuer("https://evil.example.com/realms/fintech").build();
            assertThat(v.validate(jwt).hasErrors()).isTrue();
        }

        @Test
        void rejects_wrong_audience() {
            OAuth2TokenValidator<Jwt> v = JwtValidators.strict(
                    ISSUER, List.of(AUDIENCE), null, null, AZP);
            Jwt jwt = baseToken().audience(List.of("wrong-api")).build();
            assertThat(v.validate(jwt).hasErrors()).isTrue();
        }

        @Test
        void throws_when_no_expected_audiences() {
            assertThatThrownBy(() -> JwtValidators.strict(
                    ISSUER, List.of(), null, null, AZP))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}

