package com.mariosmant.fintech.infrastructure.fraud;

import com.mariosmant.fintech.domain.model.Transfer;
import com.mariosmant.fintech.domain.model.vo.FraudCheckResult;
import com.mariosmant.fintech.domain.port.outbound.FraudDetectionPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Fraud detection adapter — rule-based implementation for the POC.
 *
 * <p>Rules:
 * <ul>
 *   <li>Flag transfers exceeding 10,000 in any currency as high-risk.</li>
 *   <li>Flag transfers where source == target account (self-transfer) as suspicious.</li>
 *   <li>All other transfers are approved with a low risk score.</li>
 * </ul>
 *
 * <p>In production, this would call an external fraud-detection ML service or
 * rules engine. Wrapped with Resilience4j {@code @CircuitBreaker} and
 * {@code @Retry} with exponential backoff.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Component
public class FraudDetectionAdapter implements FraudDetectionPort {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionAdapter.class);
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("10000");

    @Override
    @CircuitBreaker(name = "fraudDetection", fallbackMethod = "fallback")
    @Retry(name = "fraudDetection")
    public FraudCheckResult check(Transfer transfer) {
        log.info("Running fraud check for transfer: {}", transfer.getId());

        // Rule 1: High-value transfer
        if (transfer.getSourceAmount().amount().compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            log.warn("High-value transfer detected: {} (amount={})",
                    transfer.getId(), transfer.getSourceAmount());
            return FraudCheckResult.rejected(
                    "High-value transfer exceeds threshold of " + HIGH_VALUE_THRESHOLD, 85);
        }

        // Rule 2: Self-transfer detection
        if (transfer.getSourceAccountId().equals(transfer.getTargetAccountId())) {
            log.warn("Self-transfer detected: {}", transfer.getId());
            return FraudCheckResult.rejected("Self-transfer not allowed", 70);
        }

        // All checks passed
        return FraudCheckResult.approved(10);
    }

    /**
     * Circuit breaker fallback — returns a cautious rejection when the
     * fraud service is unavailable.
     */
    @SuppressWarnings("unused")
    private FraudCheckResult fallback(Transfer transfer, Throwable ex) {
        log.error("Fraud detection circuit breaker open for transfer: {}",
                transfer.getId(), ex);
        return FraudCheckResult.rejected(
                "Fraud detection service unavailable — transaction held for review", 50);
    }
}

