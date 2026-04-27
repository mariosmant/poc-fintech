package com.mariosmant.fintech.infrastructure.security.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * picks the {@link RateLimitPolicy} for an incoming
 * request.
 *
 * <p>The default implementation ({@link PathPatternBucketPolicyResolver})
 * matches request URIs against an ordered list of Ant-style path patterns
 * configured under {@code app.security.rate-limit.policies.*.path-pattern}.
 * The first match wins; unmatched requests use
 * {@code app.security.rate-limit.default-policy.*}.</p>
 *
 * <h2>Why a port?</h2>
 * <p>Future work will introduce tenant- and reputation-aware
 * resolvers ({@code tenant:&lt;id&gt;:user:&lt;sub&gt;} keys, IP-reputation
 * downgrades). Those plug in behind this interface without touching the
 * filter or the limiter adapters — the same hexagonal-architecture seam
 * for the limiter itself.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public interface BucketPolicyResolver {

    /**
     * Pick a policy for {@code request}. MUST never return {@code null}; if
     * no specific policy matches, return the configured default policy.
     */
    RateLimitPolicy resolve(HttpServletRequest request);
}

