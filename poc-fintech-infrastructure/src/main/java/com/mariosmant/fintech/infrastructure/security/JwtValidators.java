package com.mariosmant.fintech.infrastructure.security;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Ultra-strict JWT validator factory.
 *
 * <p>Composes, in order, the following fail-closed validators:
 * <ol>
 *   <li>{@link JwtTimestampValidator} with an explicit, small clock-skew
 *       (default 30s, configurable) — enforces {@code exp} / {@code nbf}.</li>
 *   <li>{@link JwtIssuerValidator} — pins the expected {@code iss} claim.</li>
 *   <li>{@link #audience(List)} — at least one of the expected audiences must
 *       appear in {@code aud}. Accepts both string and array forms.</li>
 *   <li>{@link #tokenType()} — rejects non-{@code JWT} {@code typ} headers
 *       (absent {@code typ} is tolerated; many IdPs omit it).</li>
 *   <li>{@link #requiredClaims(List)} — every listed claim must be present and
 *       non-blank (defaults: {@code sub}, {@code iat}, {@code exp}).</li>
 *   <li>{@link #authorizedParty(String)} — when configured, {@code azp} must
 *       equal the expected client id.</li>
 * </ol>
 *
 * <p><b>Scope.</b> This class composes <em>claim-level</em> validations only.
 * JWS algorithm pinning (mitigating {@code alg:none} and RS→HS confusion)
 * is applied on the decoder side; see
 * {@link JwtDecoderConfig}. Keeping the two concerns separate makes each
 * unit-testable and keeps the Spring wiring out of the validator logic.</p>
 *
 * <p>Error codes follow
 * <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-5.2">RFC 6749 §5.2</a>:
 * failures are reported as {@code invalid_token}.</p>
 *
 * @since 1.0.0
 */
public final class JwtValidators {

    private JwtValidators() { /* factory */ }

    /**
     * Builds the composite strict validator.
     *
     * @param expectedIssuer         expected {@code iss} claim (required)
     * @param expectedAudiences      non-empty list; at least one must appear in {@code aud}
     * @param clockSkew              max accepted drift for {@code exp}/{@code nbf}; {@code null} → 30s
     * @param requiredClaims         claims that must be present and non-blank;
     *                               {@code null}/empty → {@code sub, iat, exp}
     * @param expectedAuthorizedParty when non-blank, enforces {@code azp} equality;
     *                                otherwise the check is skipped
     * @return a {@link DelegatingOAuth2TokenValidator} composing all strict checks
     */
    public static OAuth2TokenValidator<Jwt> strict(String expectedIssuer,
                                                   List<String> expectedAudiences,
                                                   Duration clockSkew,
                                                   List<String> requiredClaims,
                                                   String expectedAuthorizedParty) {
        Objects.requireNonNull(expectedIssuer, "expectedIssuer must not be null");
        Objects.requireNonNull(expectedAudiences, "expectedAudiences must not be null");
        if (expectedAudiences.isEmpty()) {
            throw new IllegalArgumentException("At least one expected audience must be configured");
        }
        Duration skew = (clockSkew == null) ? Duration.ofSeconds(30) : clockSkew;
        List<String> required = (requiredClaims == null || requiredClaims.isEmpty())
                ? List.of(JwtClaimNames.SUB, JwtClaimNames.IAT, JwtClaimNames.EXP)
                : requiredClaims;

        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(skew),
                new JwtIssuerValidator(expectedIssuer),
                audience(expectedAudiences),
                tokenType(),
                requiredClaims(required),
                authorizedParty(expectedAuthorizedParty));
    }

    /** Validator that requires the {@code aud} claim to contain at least one expected value. */
    static OAuth2TokenValidator<Jwt> audience(List<String> expected) {
        final List<String> expectedCopy = List.copyOf(expected);
        return jwt -> {
            Object raw = jwt.getClaim("aud");
            Collection<String> actual;
            if (raw instanceof Collection<?> c) {
                actual = c.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList();
            } else if (raw instanceof String s) {
                actual = List.of(s);
            } else {
                return failure("Missing or unparseable 'aud' claim");
            }
            for (String exp : expectedCopy) {
                if (actual.contains(exp)) {
                    return OAuth2TokenValidatorResult.success();
                }
            }
            return failure("Required audience not present. Expected one of " + expectedCopy);
        };
    }

    /**
     * Rejects unknown {@code typ} headers. A missing {@code typ} is tolerated because many
     * IdPs (including default Keycloak configurations) omit it; RFC 7519 §5.1 makes it optional.
     */
    static OAuth2TokenValidator<Jwt> tokenType() {
        return jwt -> {
            Object typ = jwt.getHeaders().get("typ");
            if (typ == null) {
                return OAuth2TokenValidatorResult.success();
            }
            String typString = typ.toString();
            if ("JWT".equalsIgnoreCase(typString) || "at+jwt".equalsIgnoreCase(typString)) {
                return OAuth2TokenValidatorResult.success();
            }
            return failure("Unsupported token type header 'typ': " + typString);
        };
    }

    /** Validator requiring that every listed claim is present and non-blank. */
    static OAuth2TokenValidator<Jwt> requiredClaims(List<String> required) {
        final List<String> requiredCopy = List.copyOf(required);
        return jwt -> {
            for (String claim : requiredCopy) {
                Object v = jwt.getClaim(claim);
                if (v == null) {
                    return failure("Missing required claim: " + claim);
                }
                if (v instanceof String s && s.isBlank()) {
                    return failure("Required claim is blank: " + claim);
                }
            }
            return OAuth2TokenValidatorResult.success();
        };
    }

    /**
     * Validator that, when a non-blank expected value is supplied, enforces
     * {@code azp} equality. If the token omits {@code azp} (allowed by the spec when only
     * one audience is present) the check passes — audience validation remains the
     * primary control.
     */
    static OAuth2TokenValidator<Jwt> authorizedParty(String expected) {
        if (expected == null || expected.isBlank()) {
            return jwt -> OAuth2TokenValidatorResult.success();
        }
        final String expectedCopy = expected;
        return jwt -> {
            String azp = jwt.getClaimAsString("azp");
            if (azp == null) {
                return OAuth2TokenValidatorResult.success();
            }
            if (expectedCopy.equals(azp)) {
                return OAuth2TokenValidatorResult.success();
            }
            return failure("Unexpected 'azp' (authorized party): " + azp);
        };
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token",
                description,
                "https://datatracker.ietf.org/doc/html/rfc6749#section-5.2"));
    }
}

