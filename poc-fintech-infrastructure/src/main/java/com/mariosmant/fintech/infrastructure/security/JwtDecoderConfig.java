package com.mariosmant.fintech.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Duration;
import java.util.List;

/**
 * Production {@link JwtDecoder} with strict JWS algorithm pinning and the composite
 * claim validator chain from {@link JwtValidators}.
 *
 * <p><b>Why algorithm pinning matters.</b> Without explicit pinning, a decoder will
 * accept any algorithm the JWK set advertises. Attackers that can influence the JWK
 * endpoint or replay a token signed with an unexpected algorithm (e.g. {@code alg:none},
 * HS256 using the public key as a shared secret) can bypass signature verification.
 * This class restricts accepted algorithms to an allow-list
 * (defaults: {@code RS256, PS256}, both NIST-aligned asymmetric schemes and compatible
 * with Keycloak's defaults).</p>
 *
 * <p><b>Profile gating.</b> Excluded from the {@code test} profile so Testcontainers /
 * mock-JWT test slices can substitute their own decoder.</p>
 *
 * @see JwtValidators
 * @since 1.0.0
 */
@Configuration
@Profile("!test")
public class JwtDecoderConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    /** Comma-separated list; at least one value must appear in the token's {@code aud}. */
    @Value("${app.security.jwt.audiences}")
    private List<String> expectedAudiences;

    @Value("${app.security.jwt.clock-skew-seconds:30}")
    private long clockSkewSeconds;

    /** Comma-separated list of accepted JWS signing algorithms (e.g. {@code RS256,PS256}). */
    @Value("${app.security.jwt.allowed-algorithms:RS256,PS256}")
    private List<String> allowedAlgorithms;

    /** Comma-separated list of required claims that must be present and non-blank. */
    @Value("${app.security.jwt.required-claims:sub,iat,exp}")
    private List<String> requiredClaims;

    /**
     * Optional expected {@code azp} (authorized party). Leave blank to skip.
     * Typical value: the Keycloak client id that end-user browsers authenticate with.
     */
    @Value("${app.security.jwt.authorized-party:}")
    private String authorizedParty;

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder.JwkSetUriJwtDecoderBuilder builder =
                NimbusJwtDecoder.withJwkSetUri(jwkSetUri);
        // Pin accepted JWS algorithms — fail-closed against algorithm-confusion attacks.
        builder.jwsAlgorithms(set -> {
            for (String alg : allowedAlgorithms) {
                set.add(SignatureAlgorithm.from(alg.trim()));
            }
        });
        NimbusJwtDecoder decoder = builder.build();

        OAuth2TokenValidator<Jwt> validator = JwtValidators.strict(
                issuerUri,
                expectedAudiences,
                Duration.ofSeconds(clockSkewSeconds),
                requiredClaims,
                authorizedParty);
        decoder.setJwtValidator(validator);
        return decoder;
    }
}

