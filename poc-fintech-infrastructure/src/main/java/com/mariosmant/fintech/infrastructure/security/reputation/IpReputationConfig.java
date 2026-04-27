package com.mariosmant.fintech.infrastructure.security.reputation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *wires the {@link IpReputationService}, the
 * {@link BlockedIpFilter} (with explicit pre-RateLimitFilter ordering),
 * and the optional Spamhaus DROP / EDROP feed refresher.
 *
 * <p>The whole stack is gated by {@code app.security.ip-reputation.enabled}
 * (default {@code false}) so the POC starts with zero outbound network
 * coupling. Operators flip the property and supply
 * {@code app.security.ip-reputation.sources} to activate Spamhaus DROP +
 * EDROP refreshing on the configured interval (default 1 hour).</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IpReputationProperties.class)
@ConditionalOnProperty(name = "app.security.ip-reputation.enabled", havingValue = "true")
public class IpReputationConfig {

    private static final Logger log = LoggerFactory.getLogger(IpReputationConfig.class);

    /** Order pinned ahead of the bare-bean-registered RateLimitFilter. */
    public static final int FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    @Bean
    public IpReputationService ipReputationService(IpReputationProperties props) {
        CaffeineIpReputationService svc = new CaffeineIpReputationService();
        // Seed with any operator-supplied static entries so the filter is
        // immediately useful even before the first HTTP refresh succeeds.
        if (!props.getStaticBlockList().isEmpty()) {
            svc.replace(props.getStaticBlockList());
            log.info("IP-reputation seeded with {} static entries.",
                    props.getStaticBlockList().size());
        }
        return svc;
    }

    @Bean
    public FilterRegistrationBean<BlockedIpFilter> blockedIpFilterRegistration(
            IpReputationService reputation, ObjectMapper objectMapper) {
        BlockedIpFilter filter = new BlockedIpFilter(reputation, objectMapper);
        FilterRegistrationBean<BlockedIpFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(FILTER_ORDER);
        registration.addUrlPatterns("/api/*");
        registration.setName("blockedIpFilter");
        return registration;
    }

    /**
     * Best-effort scheduled refresher. Pulls each configured feed URL,
     * parses CIDR entries, and atomically swaps the snapshot. Failures are
     * logged at WARN; the previous snapshot is preserved (fail-static).
     *
     * <p>Note: depends on {@code @EnableScheduling} being active (already
     * enabled by outbox shedder); otherwise the bean is wired
     * but the scheduled method is never invoked.</p>
     *
     * <p>Conditional on at least one feed source being configured —
     * single-deployment POCs that only use a {@code static-block-list}
     * don't need the HTTP poller.</p>
     */
    @Bean
    @ConditionalOnProperty(name = "app.security.ip-reputation.sources[0]")
    public IpReputationFeedRefresher ipReputationFeedRefresher(
            CaffeineIpReputationService svc, IpReputationProperties props) {
        return new IpReputationFeedRefresher(svc, props);
    }

    /** Package-private refresher; isolated for unit testing. */
    static final class IpReputationFeedRefresher {
        private final CaffeineIpReputationService svc;
        private final IpReputationProperties props;
        private final HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        IpReputationFeedRefresher(CaffeineIpReputationService svc, IpReputationProperties props) {
            this.svc = svc;
            this.props = props;
            log.info("IP-reputation refresher armed: {} source(s), interval={}",
                    props.getSources().size(), props.getRefreshInterval());
        }

        @org.springframework.scheduling.annotation.Scheduled(
                fixedDelayString = "${app.security.ip-reputation.refresh-interval-ms:3600000}",
                initialDelayString = "${app.security.ip-reputation.initial-delay-ms:60000}")
        public void refresh() {
            List<String> all = new ArrayList<>(props.getStaticBlockList());
            for (String url : props.getSources()) {
                try {
                    List<String> entries = fetch(url);
                    all.addAll(entries);
                    log.info("IP-reputation feed pulled url={} entries={}", url, entries.size());
                } catch (IOException | InterruptedException ex) {
                    log.warn("IP-reputation feed pull failed url={} cause={}", url, ex.toString());
                    if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                }
            }
            if (!all.isEmpty()) {
                svc.replace(all);
            } else {
                log.warn("IP-reputation refresh produced zero entries — keeping previous snapshot ({} entries).",
                        svc.size());
            }
        }

        List<String> fetch(String url) throws IOException, InterruptedException {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "poc-fintech/IpReputationRefresher")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("IP-reputation feed HTTP non-2xx url={} status={}", url, resp.statusCode());
                return Collections.emptyList();
            }
            return resp.body().lines().toList();
        }
    }
}


