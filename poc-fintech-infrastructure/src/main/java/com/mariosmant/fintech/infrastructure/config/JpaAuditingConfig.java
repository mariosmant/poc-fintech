package com.mariosmant.fintech.infrastructure.config;

import com.mariosmant.fintech.infrastructure.security.SecurityContextUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

/**
 * JPA Auditing configuration — enables automatic population of
 * {@code @CreatedDate}, {@code @LastModifiedDate}, and {@code @CreatedBy} fields.
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    /**
     * Provides the current auditor (user) for {@code @CreatedBy} / {@code @LastModifiedBy}.
     * Extracts the authenticated principal from the security context (JWT sub claim).
     * Falls back to "system" for non-authenticated contexts (e.g., scheduled tasks, migrations).
     *
     * @return the auditor provider
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            try {
                return Optional.of(SecurityContextUtil.getAuthenticatedUserId());
            } catch (IllegalStateException e) {
                return Optional.of("system");
            }
        };
    }
}

