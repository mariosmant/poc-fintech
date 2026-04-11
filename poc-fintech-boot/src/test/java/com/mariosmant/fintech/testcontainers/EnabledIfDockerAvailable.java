package com.mariosmant.fintech.testcontainers;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * JUnit 5 condition that skips tests when Docker is not available.
 *
 * <p>Integration and E2E tests that rely on Testcontainers should be annotated
 * with this to gracefully skip instead of failing when Docker is not running.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(EnabledIfDockerAvailable.DockerAvailableCondition.class)
public @interface EnabledIfDockerAvailable {

    class DockerAvailableCondition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            try {
                DockerClientFactory.instance().client();
                return ConditionEvaluationResult.enabled("Docker is available");
            } catch (Throwable ex) {
                return ConditionEvaluationResult.disabled(
                        "Docker is not available — skipping Testcontainers test: " + ex.getMessage());
            }
        }
    }
}

