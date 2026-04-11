package com.mariosmant.fintech.domain.model.vo;

/**
 * Value Object capturing the result of a fraud detection check.
 *
 * @param approved  {@code true} if the transaction passed fraud screening
 * @param reason    human-readable reason (populated on rejection)
 * @param riskScore numeric risk score (0–100); higher = riskier
 * @author mariosmant
 * @since 1.0.0
 */
public record FraudCheckResult(boolean approved, String reason, int riskScore) {

    /**
     * Factory: creates an approved result.
     *
     * @param riskScore the assessed risk score
     * @return an approved {@code FraudCheckResult}
     */
    public static FraudCheckResult approved(int riskScore) {
        return new FraudCheckResult(true, "Approved", riskScore);
    }

    /**
     * Factory: creates a rejected result.
     *
     * @param reason    the rejection reason
     * @param riskScore the assessed risk score
     * @return a rejected {@code FraudCheckResult}
     */
    public static FraudCheckResult rejected(String reason, int riskScore) {
        return new FraudCheckResult(false, reason, riskScore);
    }
}

