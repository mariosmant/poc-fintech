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
 * {@code FxRateAdapter} — outbound adapter for the {@link FxRatePort} port.
 * Provides foreign-exchange conversion rates for cross-currency transfers.
 *
 * <h2>Architecture role</h2>
 * <ul>
 *   <li><b>Hexagonal / Ports &amp; Adapters:</b> stand-in for a market-data
 *       feed (ECB daily reference, Bloomberg BXT, Refinitiv) behind the
 *       {@link FxRatePort} domain port. Everything downstream sees only the
 *       port — the saga has no idea whether rates came from a static table,
 *       a WebSocket feed, or an HTTP cache.</li>
 *   <li><b>Rate triangulation through USD:</b> the internal table stores one
 *       rate per currency (all against USD). A cross-pair such as
 *       {@code EUR → GBP} is computed as
 *       {@code rate(USD → GBP) / rate(USD → EUR)} — O(N) storage instead of
 *       O(N²) of a pair-table, and mathematically consistent (no missing
 *       diagonals, no inverse inconsistencies).</li>
 *   <li><b>Resilience4j Circuit Breaker + Retry:</b> identical wrapping to
 *       {@code FraudDetectionAdapter}; in production a stale rate is better
 *       than a transfer outage, so the circuit's fallback returns the most
 *       recent cached rate (TODO when the cache is wired) with a widened
 *       spread.</li>
 * </ul>
 *
 * <h2>Fintech concepts</h2>
 * <ul>
 *   <li><b>Banker's rounding</b> is applied via {@link Money}; the conversion
 *       itself uses {@link BigDecimal} throughout — see
 *       {@link com.mariosmant.fintech.domain.model.vo.Money} for the rationale
 *       against floating-point in monetary code.</li>
 *   <li><b>Bid/ask spread</b> is a single mid-market rate in this POC; real
 *       deployments would expose separate buy/sell sides and apply a merchant
 *       spread on the application side.</li>
 *   <li><b>PSD2 price transparency (RTS Art. 59)</b> — the rate and its
 *       timestamp are persisted on the {@link com.mariosmant.fintech.domain.model.Transfer}
 *       aggregate so the customer statement can show the exact conversion
 *       applied at the time of the transfer.</li>
 * </ul>
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

