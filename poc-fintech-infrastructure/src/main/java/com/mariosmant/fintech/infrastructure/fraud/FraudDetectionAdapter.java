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
 * {@code FraudDetectionAdapter} — outbound adapter for the {@link FraudDetectionPort}
 * port. Screens transfers before any balance mutation.
 *
 * <h2>Architecture role</h2>
 * <ul>
 *   <li><b>Hexagonal / Ports &amp; Adapters:</b> the {@link FraudDetectionPort}
 *       interface is a <i>domain port</i>. This class is the <i>infrastructure
 *       adapter</i> — a stand-in for what would be an HTTPS call to a real
 *       fraud-scoring service (Featurespace / NICE Actimize / a home-grown ML
 *       model). Swapping the implementation never touches domain code.</li>
 *   <li><b>Circuit Breaker (Nygard — "Release It!", Ch. 5):</b> Resilience4j's
 *       {@code @CircuitBreaker} sheds load when the upstream service is
 *       unhealthy (50% failure rate over a 10-call sliding window opens the
 *       breaker for 10 seconds). This prevents cascading failure where a
 *       slow fraud provider would otherwise tie up Kafka consumer threads
 *       and stall every transfer in the system.</li>
 *   <li><b>Retry with exponential backoff:</b> transient 5xx/timeout errors
 *       are retried 3 times starting at 500 ms with x2 backoff — eliminates
 *       noise from single-packet network blips without amplifying real
 *       outages.</li>
 *   <li><b>Fallback:</b> when the breaker is open, {@link #fallback} returns
 *       a conservative "deny with low confidence" result. <b>Never</b> return
 *       "approve" as a fallback — that converts availability incidents into
 *       fraud losses.</li>
 * </ul>
 *
 * <h2>Rule set (POC)</h2>
 * <ul>
 *   <li>Transfers ≥ {@value #HIGH_VALUE_THRESHOLD} in any currency are flagged
 *       as high-risk.</li>
 *   <li>Self-transfers (source = target) are flagged as suspicious.</li>
 *   <li>Everything else is approved with a low risk score.</li>
 * </ul>
 *
 * <h2>Regulatory context</h2>
 * <ul>
 *   <li><b>PSD2 RTS on SCA (Art. 18)</b> — Transaction Risk Analysis exemption
 *       requires a real-time fraud score before authentication. A real fraud
 *       adapter feeds this score back to the Keycloak step-up flow.</li>
 *   <li><b>PCI DSS §6.2.4</b> — common coding-vulnerability considerations:
 *       the decision is taken server-side only; client-supplied risk data is
 *       treated as hostile input.</li>
 *   <li><b>AML / BSA</b> — high-value flags would feed a Suspicious Activity
 *       Report (SAR) workflow in production.</li>
 * </ul>
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

