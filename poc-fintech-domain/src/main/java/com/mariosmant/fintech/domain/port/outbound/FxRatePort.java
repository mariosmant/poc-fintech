package com.mariosmant.fintech.domain.port.outbound;

import com.mariosmant.fintech.domain.model.vo.Currency;
import com.mariosmant.fintech.domain.model.vo.ExchangeRate;

/**
 * Outbound port for obtaining foreign-exchange rates.
 *
 * @author mariosmant
 * @since 1.0.0
 */
public interface FxRatePort {

    /**
     * Gets the current exchange rate between two currencies.
     *
     * @param source the source (base) currency
     * @param target the target (quote) currency
     * @return the current exchange rate
     */
    ExchangeRate getRate(Currency source, Currency target);
}

