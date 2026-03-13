package com.triagemate.triage.control.ai;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AiHealthIndicatorTest {

    private final AiAdvisoryProperties properties = new AiAdvisoryProperties(
            true, "anthropic",
            Set.of("DEVICE_ERROR", "NORMAL"),
            new AiAdvisoryProperties.Timeouts(Duration.ofSeconds(5)),
            new AiAdvisoryProperties.Cost(0.05, 100.0),
            new AiAdvisoryProperties.Validation(0.70, 0.85)
    );

    @Test
    void health_closedCircuitBreaker_returnsUp() {
        CircuitBreaker cb = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
                .circuitBreaker("health-closed");

        AiHealthIndicator indicator = new AiHealthIndicator(cb, properties);
        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("CLOSED", health.getDetails().get("circuitBreaker"));
        assertEquals("anthropic", health.getDetails().get("provider"));
        assertEquals(true, health.getDetails().get("enabled"));
    }

    @Test
    void health_openCircuitBreaker_returnsDown() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .build();
        CircuitBreaker cb = CircuitBreakerRegistry.of(config).circuitBreaker("health-open");

        // Force open
        cb.transitionToOpenState();

        AiHealthIndicator indicator = new AiHealthIndicator(cb, properties);
        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("OPEN", health.getDetails().get("circuitBreaker"));
    }

    @Test
    void health_halfOpenCircuitBreaker_returnsUp() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .waitDurationInOpenState(Duration.ofMillis(50))
                .build();
        CircuitBreaker cb = CircuitBreakerRegistry.of(config).circuitBreaker("health-half-open");

        cb.transitionToOpenState();
        cb.transitionToHalfOpenState();

        AiHealthIndicator indicator = new AiHealthIndicator(cb, properties);
        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("HALF_OPEN", health.getDetails().get("circuitBreaker"));
    }
}
