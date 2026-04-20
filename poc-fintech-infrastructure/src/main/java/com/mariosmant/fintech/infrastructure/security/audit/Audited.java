package com.mariosmant.fintech.infrastructure.security.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method for audit logging.
 * The AuditAspect will capture user ID, action, resource details, IP address,
 * and persist to the audit_log table.
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** Human-readable action name (e.g., "CREATE_ACCOUNT", "INITIATE_TRANSFER"). */
    String action();

    /** Resource type being acted upon (e.g., "Account", "Transfer"). */
    String resourceType() default "";
}

