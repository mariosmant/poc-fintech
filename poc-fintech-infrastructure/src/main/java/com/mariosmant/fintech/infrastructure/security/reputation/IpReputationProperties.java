package com.mariosmant.fintech.infrastructure.security.reputation;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 *bound configuration for the IP-reputation
 * pre-filter.
 *
 * <p>Property prefix {@code app.security.ip-reputation}.</p>
 *
 * <pre>
 * app.security.ip-reputation:
 *   enabled: true
 *   refresh-interval: PT1H
 *   sources:
 *     - https://www.spamhaus.org/drop/drop.txt
 *     - https://www.spamhaus.org/drop/edrop.txt
 *   static-block-list: []         # additional CIDR entries from ops
 * </pre>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "app.security.ip-reputation")
public class IpReputationProperties {

    private boolean enabled = false;
    private Duration refreshInterval = Duration.ofHours(1);
    private List<String> sources = new ArrayList<>();
    private List<String> staticBlockList = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Duration getRefreshInterval() { return refreshInterval; }
    public void setRefreshInterval(Duration refreshInterval) { this.refreshInterval = refreshInterval; }

    public List<String> getSources() { return sources; }
    public void setSources(List<String> sources) { this.sources = sources; }

    public List<String> getStaticBlockList() { return staticBlockList; }
    public void setStaticBlockList(List<String> staticBlockList) { this.staticBlockList = staticBlockList; }
}

