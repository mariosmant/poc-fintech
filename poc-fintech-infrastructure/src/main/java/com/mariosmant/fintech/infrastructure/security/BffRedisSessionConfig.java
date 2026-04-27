package com.mariosmant.fintech.infrastructure.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * distributed HttpSession storage for the {@code bff} profile.
 *
 * <h2>Why</h2>
 * <p>The BFF stores the OAuth2 access/refresh token in the
 * server-side {@link jakarta.servlet.http.HttpSession}. With the default
 * Tomcat session manager this state is per-pod, so a horizontal scale-up
 * forces sticky sessions — which conflicts with rolling deploys and zero-
 * downtime restarts. Spring Session backed by Redis externalises the session
 * map: any pod can serve any request, and a pod restart loses no sessions.</p>
 *
 * <h2>Activation</h2>
 * <p>This config is gated by {@code spring.session.store-type=redis}. The
 * default (in {@code application.yml}'s {@code bff} profile) is {@code none},
 * which keeps the existing in-memory Tomcat session — so the
 * {@code BffSecurityConfigSmokeTest} (which boots the full BFF stack without
 * a Redis testcontainer) still passes. A real BFF deployment sets
 * {@code BFF_SESSION_STORE=redis} alongside {@code REDIS_HOST/PORT/PASSWORD}.</p>
 *
 * <h2>Cookie attributes preserved</h2>
 * <p>The Spring Session {@link CookieSerializer} replaces the Tomcat default,
 * so we re-assert the {@code __Host-} prefix attributes here:
 * {@code Secure}, {@code SameSite=Strict}, {@code Path=/}, no {@code Domain}.
 * Without this, Spring Session would emit a stock {@code SESSION} cookie and
 * the RFC 6265bis §4.1.3.2 origin-binding would silently regress.</p>
 *
 * <h2>Standards mapping</h2>
 * <ul>
 *   <li>OWASP ASVS V3.2.2 — server-side session state.</li>
 *   <li>OWASP ASVS V3.4.1/4.3 — session cookie attributes.</li>
 *   <li>PCI DSS v4.0.1 §8.2.8 — re-auth after 15 min idle (timeout from
 *       {@code server.servlet.session.timeout} is honoured by Spring Session).</li>
 *   <li>NIST SP 800-63B §7.1 — managed session reauthentication.</li>
 * </ul>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@Profile("bff")
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
public class BffRedisSessionConfig {

    /**
     * Re-applies the {@code __Host-SESSION} cookie attributes after Spring
     * Session takes over from Tomcat's session cookie writer.
     */
    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("__Host-SESSION");
        serializer.setCookiePath("/");
        serializer.setUseSecureCookie(true);
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Strict");
        // RFC 6265bis §4.1.3.2: __Host- prefix forbids `Domain`. Spring Session's
        // default already omits it; we assert by NOT calling setDomainName(...).
        return serializer;
    }
}

