package com.triagemate.triage.control.ai;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AiMetrics {

    private final MeterRegistry registry;

    public AiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Registers a gauge that exposes the circuit breaker state as a numeric value.
     * 0 = CLOSED, 1 = OPEN, 2 = HALF_OPEN, -1 = other.
     */
    public void registerCircuitBreakerStateGauge(CircuitBreaker circuitBreaker, String provider) {
        Gauge.builder("triagemate.ai.circuit.breaker.state", circuitBreaker,
                        cb -> switch (cb.getState()) {
                            case CLOSED -> 0;
                            case OPEN -> 1;
                            case HALF_OPEN -> 2;
                            default -> -1;
                        })
                .tag("provider", provider)
                .description("Circuit breaker state: 0=CLOSED, 1=OPEN, 2=HALF_OPEN")
                .register(registry);
    }

    public void recordCall(String provider, String status, long latencyMs) {
        Counter.builder("triagemate.ai.calls.total")
                .tag("provider", provider != null ? provider : "unknown")
                .tag("status", status)
                .register(registry)
                .increment();

        Timer.builder("triagemate.ai.latency.seconds")
                .tag("provider", provider != null ? provider : "unknown")
                .register(registry)
                .record(Duration.ofMillis(latencyMs));
    }

    public void recordCost(String provider, double costUsd) {
        Counter.builder("triagemate.ai.cost.usd.total")
                .tag("provider", provider != null ? provider : "unknown")
                .register(registry)
                .increment(costUsd);
    }

    public void recordAdviceAccepted() {
        Counter.builder("triagemate.ai.advice.accepted.total")
                .register(registry)
                .increment();
    }

    public void recordAdviceRejected() {
        Counter.builder("triagemate.ai.advice.rejected.total")
                .register(registry)
                .increment();
    }

    public void recordFallback(String reason) {
        Counter.builder("triagemate.ai.fallback.total")
                .tag("reason", reason)
                .register(registry)
                .increment();
    }
}
