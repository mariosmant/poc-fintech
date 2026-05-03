package com.mariosmant.fintech.infrastructure.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.header.writers.CrossOriginEmbedderPolicyHeaderWriter;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

/**
 * Shared response-header configuration for all filter chains
 * (Resource-Server and OAuth2-Login BFF).
 *
 * <p>Keeping this in one place guarantees that the NIST SC-8/SC-18, and
 * SOC 2 CC6.6 header controls stay identical regardless of which authentication
 * model is active — an auditor reading either filter chain sees the same hardened
 * surface.</p>
 *
 * <p>See {@link SecurityConfig} Javadoc for the full standards mapping.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
final class SecurityHeaders {

    private SecurityHeaders() {
    }

    /**
     * Applies the full hardened header set to the supplied {@link HttpSecurity}:
     * HSTS, CSP (strict), COOP/COEP/CORP, Referrer-Policy, Permissions-Policy,
     * X-Content-Type-Options, X-Frame-Options=DENY, XSS-Protection disabled
     * (per modern browser guidance).
     */
    static HttpSecurity apply(HttpSecurity http) throws Exception {
        return http.headers(headers -> headers
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .preload(true)
                        .maxAgeInSeconds(31_536_000))
                .contentTypeOptions(contentType -> {
                })
                .frameOptions(frame -> frame.deny())
                .xssProtection(xss ->
                        xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.DISABLED))
                .referrerPolicy(referrer ->
                        referrer.policy(ReferrerPolicyHeaderWriter
                                .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .crossOriginOpenerPolicy(coop -> coop.policy(
                        CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy.SAME_ORIGIN))
                .crossOriginEmbedderPolicy(coep -> coep.policy(
                        CrossOriginEmbedderPolicyHeaderWriter.CrossOriginEmbedderPolicy.REQUIRE_CORP))
                .crossOriginResourcePolicy(corp -> corp.policy(
                        CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy.SAME_ORIGIN))
                .contentSecurityPolicy(csp -> csp
                        .policyDirectives(
                                "default-src 'self'; " +
                                "script-src 'self'; " +
                                "script-src-attr 'none'; " +
                                "style-src 'self' 'unsafe-inline'; " +
                                "img-src 'self' data:; " +
                                "font-src 'self'; " +
                                "connect-src 'self'; " +
                                "frame-ancestors 'none'; " +
                                "form-action 'self'; " +
                                "base-uri 'self'; " +
                                "object-src 'none'; " +
                                "upgrade-insecure-requests"))
                .permissionsPolicy(permissions -> permissions
                        .policy("accelerometer=(), autoplay=(), browsing-topics=(), " +
                                "camera=(), cross-origin-isolated=(), display-capture=(), " +
                                "encrypted-media=(), fullscreen=(), geolocation=(), " +
                                "gyroscope=(), hid=(), identity-credentials-get=(), " +
                                "idle-detection=(), magnetometer=(), microphone=(), " +
                                "midi=(), payment=(), picture-in-picture=(), " +
                                "publickey-credentials-create=(), publickey-credentials-get=(), " +
                                "screen-wake-lock=(), serial=(), storage-access=(), " +
                                "usb=(), web-share=(), xr-spatial-tracking=()"))
        );
    }
}

