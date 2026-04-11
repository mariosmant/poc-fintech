package com.mariosmant.fintech.domain.model.vo;

/**
 * Supported currencies following ISO 4217.
 *
 * <p>Restricting to a known enum set prevents injection of unsupported
 * currency codes — an OWASP input-validation best practice.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
public enum Currency {

    /** United States Dollar */
    USD(2),
    /** Euro */
    EUR(2),
    /** British Pound Sterling */
    GBP(2),
    /** Japanese Yen — uses 0 decimal places per ISO 4217 */
    JPY(0),
    /** Swiss Franc */
    CHF(2);

    private final int decimalPlaces;

    Currency(int decimalPlaces) {
        this.decimalPlaces = decimalPlaces;
    }

    /**
     * Returns the standard number of decimal places for this currency.
     *
     * @return decimal place count per ISO 4217
     */
    public int getDecimalPlaces() {
        return decimalPlaces;
    }
}

