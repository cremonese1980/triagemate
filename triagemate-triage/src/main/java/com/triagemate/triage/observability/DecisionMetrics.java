package com.triagemate.triage.observability;

import com.triagemate.triage.control.decision.DecisionOutcome;
import com.triagemate.triage.control.decision.DecisionResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Encapsulates decision-related metrics.
 * Uses dynamic tags — adding new outcomes requires no code changes.
 */
@Component
public class DecisionMetrics {

    private final MeterRegistry registry;

    public DecisionMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Record decision outcome and latency.
     */
    public void recordDecision(DecisionOutcome outcome, Duration latency) {
        String tag = outcome.name().toLowerCase();

        registry.counter("triagemate.decision.total", "outcome", tag).increment();
        registry.timer("triagemate.decision.latency.seconds", "outcome", tag)
                .record(latency);
    }

    /**
     * Record validation failure before decision execution.
     */
    public void recordInvalid() {
        registry.counter("triagemate.decision.invalid.total").increment();
    }

    /**
     * Record decision error (exception during processing).
     */
    public void recordError() {
        registry.counter("triagemate.decision.error.total").increment();
    }

    /**
     * Execute decision logic with automatic timing.
     * Records both outcome counter and latency histogram.
     * On exception, records error metric and re-throws.
     */
    public DecisionResult timeDecision(Supplier<DecisionResult> decisionLogic) {
        long startNanos = System.nanoTime();

        try {
            DecisionResult result = decisionLogic.get();
            Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
            recordDecision(result.outcome(), duration);
            return result;
        } catch (Exception e) {
            recordError();
            throw e;
        }
    }
}
