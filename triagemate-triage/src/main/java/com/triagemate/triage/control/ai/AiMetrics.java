package com.triagemate.triage.control.ai;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class AiMetrics {

    private final MeterRegistry registry;

    private final Counter adviceAccepted;
    private final Counter adviceRejected;
    private final ConcurrentMap<String, Counter> callCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> latencyTimers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> costCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> fallbackCounters = new ConcurrentHashMap<>();

    public AiMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.adviceAccepted = Counter.builder("triagemate.ai.advice.accepted.total")
                .register(registry);
        this.adviceRejected = Counter.builder("triagemate.ai.advice.rejected.total")
                .register(registry);
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
        String safeProvider = provider != null ? provider : "unknown";
        String callKey = safeProvider + ":" + status;

        callCounters.computeIfAbsent(callKey, k ->
                Counter.builder("triagemate.ai.calls.total")
                        .tag("provider", safeProvider)
                        .tag("status", status)
                        .register(registry)
        ).increment();

        latencyTimers.computeIfAbsent(safeProvider, k ->
                Timer.builder("triagemate.ai.latency.seconds")
                        .tag("provider", safeProvider)
                        .register(registry)
        ).record(Duration.ofMillis(latencyMs));
    }

    public void recordCost(String provider, double costUsd) {
        String safeProvider = provider != null ? provider : "unknown";
        costCounters.computeIfAbsent(safeProvider, k ->
                Counter.builder("triagemate.ai.cost.usd.total")
                        .tag("provider", safeProvider)
                        .register(registry)
        ).increment(costUsd);
    }

    public void recordAdviceAccepted() {
        adviceAccepted.increment();
    }

    public void recordAdviceRejected() {
        adviceRejected.increment();
    }

    public void recordFallback(String reason) {
        fallbackCounters.computeIfAbsent(reason, k ->
                Counter.builder("triagemate.ai.fallback.total")
                        .tag("reason", reason)
                        .register(registry)
        ).increment();
    }
}
