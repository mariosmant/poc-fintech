package com.mariosmant.fintech.infrastructure.security.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * first-match Ant-pattern policy resolver.
 *
 * <p>Patterns are evaluated in the order returned by
 * {@link RateLimitProperties#getPolicies()} (Spring Boot binds a
 * {@code LinkedHashMap}, preserving YAML order). The first
 * {@code path-pattern} matching the request URI wins; if none match, the
 * configured default policy is returned. Pattern matching uses Spring's
 * {@link AntPathMatcher} so {@code /api/v1/transfers/**} works as expected.</p>
 *
 * <p>This class is deliberately allocation-light on the hot path: the
 * {@code AntPathMatcher} is reused, and the immutable
 * {@link CompiledPattern} list is built once at construction.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public class PathPatternBucketPolicyResolver implements BucketPolicyResolver {

    private static final Logger log = LoggerFactory.getLogger(PathPatternBucketPolicyResolver.class);

    private final List<CompiledPattern> compiled;
    private final RateLimitPolicy defaultPolicy;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public PathPatternBucketPolicyResolver(RateLimitProperties props) {
        this.defaultPolicy = toPolicy("default", props.getDefaultPolicy());
        this.compiled = new ArrayList<>();
        for (Map.Entry<String, RateLimitProperties.PolicyDef> e : props.getPolicies().entrySet()) {
            String id = e.getKey();
            RateLimitProperties.PolicyDef def = e.getValue();
            if (def.getPathPattern() == null || def.getPathPattern().isBlank()) {
                throw new IllegalStateException(
                        "app.security.rate-limit.policies." + id + ".path-pattern must be set");
            }
            compiled.add(new CompiledPattern(def.getPathPattern(), toPolicy(id, def)));
        }
        log.info("PathPatternBucketPolicyResolver — {} route policies, default={}rpm/{}s burst={}",
                compiled.size(),
                defaultPolicy.requestsPerMinute(),
                defaultPolicy.windowSeconds(),
                defaultPolicy.burstCapacity());
    }

    @Override
    public RateLimitPolicy resolve(HttpServletRequest request) {
        String uri = Objects.requireNonNullElse(request.getRequestURI(), "");
        for (CompiledPattern p : compiled) {
            if (matcher.match(p.pattern, uri)) {
                return p.policy;
            }
        }
        return defaultPolicy;
    }

    /** Visible-for-tests. */
    RateLimitPolicy defaultPolicy() {
        return defaultPolicy;
    }

    /** Build a {@link RateLimitPolicy} from a {@link RateLimitProperties.PolicyDef}, normalising defaults. */
    private static RateLimitPolicy toPolicy(String id, RateLimitProperties.PolicyDef def) {
        int rpm = def.getRequestsPerMinute();
        int windowSec = def.getWindowSeconds();
        int burst = def.getBurstCapacity() > 0 ? def.getBurstCapacity() : rpm;
        return new RateLimitPolicy(id, rpm, windowSec, burst);
    }

    private record CompiledPattern(String pattern, RateLimitPolicy policy) {}
}

