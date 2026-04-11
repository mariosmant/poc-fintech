package com.mariosmant.fintech.domain.port.outbound;

import com.mariosmant.fintech.domain.model.Transfer;
import com.mariosmant.fintech.domain.model.vo.FraudCheckResult;

/**
 * Outbound port for the fraud detection service.
 *
 * <p>Implementations may call an external service, a rules engine,
 * or an ML model. The infrastructure adapter wraps this with
 * Circuit Breaker + Retry (Resilience4j).</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public interface FraudDetectionPort {

    /**
     * Checks the transfer for potential fraud.
     *
     * @param transfer the transfer to evaluate
     * @return the fraud check result
     */
    FraudCheckResult check(Transfer transfer);
}

