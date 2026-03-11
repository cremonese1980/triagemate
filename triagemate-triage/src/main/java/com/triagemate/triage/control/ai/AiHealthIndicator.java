package com.triagemate.triage.control.ai;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component("ai")
@ConditionalOnProperty(name = "triagemate.ai.enabled", havingValue = "true")
public class AiHealthIndicator implements HealthIndicator {

    private final CircuitBreaker circuitBreaker;
    private final AiAdvisoryProperties properties;

    public AiHealthIndicator(CircuitBreaker circuitBreaker, AiAdvisoryProperties properties) {
        this.circuitBreaker = circuitBreaker;
        this.properties = properties;
    }

    @Override
    public Health health() {
        CircuitBreaker.State state = circuitBreaker.getState();
        Health.Builder builder = switch (state) {
            case CLOSED -> Health.up();
            case HALF_OPEN -> Health.up(); // recovering
            case OPEN -> Health.down();
            default -> Health.unknown();
        };

        return builder
                .withDetail("circuitBreaker", state.name())
                .withDetail("provider", properties.provider())
                .withDetail("enabled", properties.enabled())
                .build();
    }
}
