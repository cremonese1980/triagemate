package com.triagemate.triage.control.ai;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class AiMetricsTest {

    private SimpleMeterRegistry registry;
    private AiMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AiMetrics(registry);
    }

    @Test
    void circuitBreakerStateGauge_reflectsClosed() {
        CircuitBreaker cb = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
                .circuitBreaker("gauge-closed");

        metrics.registerCircuitBreakerStateGauge(cb, "anthropic");

        Gauge gauge = registry.find("triagemate.ai.circuit.breaker.state")
                .tag("provider", "anthropic").gauge();

        assertNotNull(gauge, "Circuit breaker state gauge should be registered");
        assertEquals(0.0, gauge.value(), "CLOSED state should be 0");
    }

    @Test
    void circuitBreakerStateGauge_reflectsOpen() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .build();
        CircuitBreaker cb = CircuitBreakerRegistry.of(config).circuitBreaker("gauge-open");
        cb.transitionToOpenState();

        metrics.registerCircuitBreakerStateGauge(cb, "anthropic");

        Gauge gauge = registry.find("triagemate.ai.circuit.breaker.state")
                .tag("provider", "anthropic").gauge();

        assertNotNull(gauge);
        assertEquals(1.0, gauge.value(), "OPEN state should be 1");
    }

    @Test
    void circuitBreakerStateGauge_reflectsHalfOpen() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .waitDurationInOpenState(Duration.ofMillis(50))
                .build();
        CircuitBreaker cb = CircuitBreakerRegistry.of(config).circuitBreaker("gauge-half-open");
        cb.transitionToOpenState();
        cb.transitionToHalfOpenState();

        metrics.registerCircuitBreakerStateGauge(cb, "anthropic");

        Gauge gauge = registry.find("triagemate.ai.circuit.breaker.state")
                .tag("provider", "anthropic").gauge();

        assertNotNull(gauge);
        assertEquals(2.0, gauge.value(), "HALF_OPEN state should be 2");
    }

    @Test
    void circuitBreakerStateGauge_updatesLiveWhenStateChanges() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .waitDurationInOpenState(Duration.ofMillis(50))
                .build();
        CircuitBreaker cb = CircuitBreakerRegistry.of(config).circuitBreaker("gauge-live");

        metrics.registerCircuitBreakerStateGauge(cb, "anthropic");

        Gauge gauge = registry.find("triagemate.ai.circuit.breaker.state")
                .tag("provider", "anthropic").gauge();

        assertEquals(0.0, gauge.value(), "Initially CLOSED");

        cb.transitionToOpenState();
        assertEquals(1.0, gauge.value(), "Should reflect OPEN after transition");

        cb.transitionToHalfOpenState();
        assertEquals(2.0, gauge.value(), "Should reflect HALF_OPEN after transition");

        cb.transitionToClosedState();
        assertEquals(0.0, gauge.value(), "Should reflect CLOSED after recovery");
    }

    @Test
    void recordCall_incrementsCallCounterWithTags() {
        metrics.recordCall("anthropic", "success", 150);
        metrics.recordCall("anthropic", "error", 0);

        double successCount = registry.find("triagemate.ai.calls.total")
                .tag("provider", "anthropic")
                .tag("status", "success")
                .counter().count();
        double errorCount = registry.find("triagemate.ai.calls.total")
                .tag("provider", "anthropic")
                .tag("status", "error")
                .counter().count();

        assertEquals(1.0, successCount);
        assertEquals(1.0, errorCount);
    }

    @Test
    void recordCost_incrementsCostCounter() {
        metrics.recordCost("anthropic", 0.003);
        metrics.recordCost("anthropic", 0.005);

        double totalCost = registry.find("triagemate.ai.cost.usd.total")
                .tag("provider", "anthropic")
                .counter().count();

        assertEquals(0.008, totalCost, 0.0001);
    }

    @Test
    void recordFallback_incrementsFallbackCounterByReason() {
        metrics.recordFallback("timeout");
        metrics.recordFallback("timeout");
        metrics.recordFallback("error");

        double timeoutCount = registry.find("triagemate.ai.fallback.total")
                .tag("reason", "timeout").counter().count();
        double errorCount = registry.find("triagemate.ai.fallback.total")
                .tag("reason", "error").counter().count();

        assertEquals(2.0, timeoutCount);
        assertEquals(1.0, errorCount);
    }

    @Test
    void recordAdviceAcceptedAndRejected_incrementsSeparateCounters() {
        metrics.recordAdviceAccepted();
        metrics.recordAdviceAccepted();
        metrics.recordAdviceRejected();

        double accepted = registry.find("triagemate.ai.advice.accepted.total").counter().count();
        double rejected = registry.find("triagemate.ai.advice.rejected.total").counter().count();

        assertEquals(2.0, accepted);
        assertEquals(1.0, rejected);
    }
}
