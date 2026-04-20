package com.mariosmant.fintech.infrastructure.security.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for audit log entries.
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {
}

