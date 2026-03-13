package com.triagemate.triage.control.ai;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "triagemate.ai.enabled", havingValue = "true")
public class AiResilienceConfig {

    public static final String AI_PROVIDER_CB = "aiProvider";
    public static final String AI_PROVIDER_RETRY = "aiProviderRetry";

    @Bean
    public CircuitBreaker aiCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slowCallRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(3))
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slidingWindowSize(10)
                .recordExceptions(TransientAiException.class)
                .ignoreExceptions(PermanentAiException.class, BudgetExceededException.class)
                .build();

        return CircuitBreakerRegistry.of(config).circuitBreaker(AI_PROVIDER_CB);
    }

    @Bean
    public Retry aiRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        Duration.ofMillis(1000), 2))
                .retryOnException(e -> e instanceof TransientAiException)
                .ignoreExceptions(PermanentAiException.class, BudgetExceededException.class)
                .build();

        return RetryRegistry.of(config).retry(AI_PROVIDER_RETRY);
    }
}
