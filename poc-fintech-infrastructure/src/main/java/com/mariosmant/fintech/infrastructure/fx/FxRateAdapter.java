package com.mariosmant.fintech.infrastructure.fx;

import com.mariosmant.fintech.domain.model.vo.Currency;
import com.mariosmant.fintech.domain.model.vo.ExchangeRate;
import com.mariosmant.fintech.domain.port.outbound.FxRatePort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * FX rate adapter — provides static exchange rates for the POC.
 *
 * <p>In production, this would call an external FX rates API (e.g., ECB, Bloomberg).
 * Rates are expressed as: 1 unit of source = rate units of target.</p>
 *
 * <p>Wrapped with Resilience4j {@code @CircuitBreaker} and {@code @Retry}
 * with exponential backoff for production resilience.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Component
public class FxRateAdapter implements FxRatePort {

    private static final Logger log = LoggerFactory.getLogger(FxRateAdapter.class);

    /**
     * Static rate table — all rates relative to USD.
     * Cross-rates are computed via triangulation through USD.
     */
    private static final Map<Currency, BigDecimal> USD_RATES = Map.of(
            Currency.USD, BigDecimal.ONE,
            Currency.EUR, new BigDecimal("0.9250"),
            Currency.GBP, new BigDecimal("0.7900"),
            Currency.JPY, new BigDecimal("149.50"),
            Currency.CHF, new BigDecimal("0.8800")
    );

    @Override
    @CircuitBreaker(name = "fxRate", fallbackMethod = "fallback")
    @Retry(name = "fxRate")
    public ExchangeRate getRate(Currency source, Currency target) {
        if (source == target) {
            return new ExchangeRate(source, target, BigDecimal.ONE, Instant.now());
        }

        // Triangulate through USD: source→USD→target
        BigDecimal sourceToUsd = BigDecimal.ONE.divide(
                USD_RATES.get(source), 10, java.math.RoundingMode.HALF_EVEN);
        BigDecimal usdToTarget = USD_RATES.get(target);
        BigDecimal rate = sourceToUsd.multiply(usdToTarget)
                .setScale(8, java.math.RoundingMode.HALF_EVEN);

        log.debug("FX rate: {} → {} = {}", source, target, rate);
        return new ExchangeRate(source, target, rate, Instant.now());
    }

    /**
     * Fallback when FX service is unavailable.
     */
    @SuppressWarnings("unused")
    private ExchangeRate fallback(Currency source, Currency target, Throwable ex) {
        log.error("FX rate service unavailable for {} → {}", source, target, ex);
        throw new RuntimeException("FX rate service unavailable", ex);
    }
}

