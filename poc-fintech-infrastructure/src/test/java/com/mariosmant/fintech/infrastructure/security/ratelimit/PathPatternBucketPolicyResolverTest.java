package com.mariosmant.fintech.infrastructure.security.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * proves the path-pattern resolver respects YAML order
 * and falls back to the default policy on no match.
 */
class PathPatternBucketPolicyResolverTest {

    @Test
    @DisplayName("First matching pattern wins; unmatched URIs use default policy")
    void resolvesByPattern() {
        RateLimitProperties props = new RateLimitProperties();
        // default-policy is back-filled from the legacy single-policy fields.
        props.setRequestsPerMinute(100);
        props.setWindowSeconds(60);

        RateLimitProperties.PolicyDef transfers = new RateLimitProperties.PolicyDef();
        transfers.setPathPattern("/api/v1/transfers/**");
        transfers.setRequestsPerMinute(20);
        transfers.setWindowSeconds(60);
        transfers.setBurstCapacity(20);
        props.getPolicies().put("transfers", transfers);

        RateLimitProperties.PolicyDef accounts = new RateLimitProperties.PolicyDef();
        accounts.setPathPattern("/api/v1/accounts/**");
        accounts.setRequestsPerMinute(60);
        accounts.setWindowSeconds(60);
        accounts.setBurstCapacity(60);
        props.getPolicies().put("accounts", accounts);

        PathPatternBucketPolicyResolver resolver = new PathPatternBucketPolicyResolver(props);

        assertThat(resolver.resolve(req("/api/v1/transfers")).id()).isEqualTo("transfers");
        assertThat(resolver.resolve(req("/api/v1/transfers/abc/status")).id()).isEqualTo("transfers");
        assertThat(resolver.resolve(req("/api/v1/accounts/123")).id()).isEqualTo("accounts");
        assertThat(resolver.resolve(req("/api/v1/ledger/foo")).id()).isEqualTo("default");
        assertThat(resolver.resolve(req("/anything-else")).id()).isEqualTo("default");
    }

    @Test
    @DisplayName("Default policy is built from the back-compat single-policy fields")
    void defaultPolicyBackCompat() {
        RateLimitProperties props = new RateLimitProperties();
        props.setRequestsPerMinute(50);
        props.setWindowSeconds(30);
        props.setBurstCapacity(0); // ⇒ getBurstCapacity() returns rpm

        PathPatternBucketPolicyResolver resolver = new PathPatternBucketPolicyResolver(props);
        RateLimitPolicy d = resolver.defaultPolicy();
        assertThat(d.id()).isEqualTo("default");
        assertThat(d.requestsPerMinute()).isEqualTo(50);
        assertThat(d.windowSeconds()).isEqualTo(30);
        assertThat(d.burstCapacity()).isEqualTo(50);
    }

    private static MockHttpServletRequest req(String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRequestURI(uri);
        return r;
    }
}

