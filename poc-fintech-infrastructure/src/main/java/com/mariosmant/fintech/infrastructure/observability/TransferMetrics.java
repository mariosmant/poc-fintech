package com.mariosmant.fintech.infrastructure.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Custom Micrometer metrics for transfer operations.
 *
 * <p>Provides counters for transfer lifecycle events and timers for
 * saga step durations. Exposed via Prometheus endpoint for Grafana dashboards.</p>
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Component
public class TransferMetrics {

    private final Counter transfersInitiated;
    private final Counter transfersCompleted;
    private final Counter transfersFailed;
    private final Timer sagaStepTimer;

    public TransferMetrics(MeterRegistry registry) {
        this.transfersInitiated = Counter.builder("fintech.transfers.initiated")
                .description("Number of transfers initiated")
                .tag("app", "poc-fintech")
                .register(registry);

        this.transfersCompleted = Counter.builder("fintech.transfers.completed")
                .description("Number of transfers completed successfully")
                .tag("app", "poc-fintech")
                .register(registry);

        this.transfersFailed = Counter.builder("fintech.transfers.failed")
                .description("Number of transfers that failed")
                .tag("app", "poc-fintech")
                .register(registry);

        this.sagaStepTimer = Timer.builder("fintech.saga.step.duration")
                .description("Time taken for individual saga steps")
                .tag("app", "poc-fintech")
                .register(registry);
    }

    /** Increments the initiated counter. */
    public void incrementInitiated() {
        transfersInitiated.increment();
    }

    /** Increments the completed counter. */
    public void incrementCompleted() {
        transfersCompleted.increment();
    }

    /** Increments the failed counter. */
    public void incrementFailed() {
        transfersFailed.increment();
    }

    /**
     * Records the duration of a saga step.
     *
     * @param duration the step duration
     */
    public void recordSagaStepDuration(Duration duration) {
        sagaStepTimer.record(duration);
    }
}

