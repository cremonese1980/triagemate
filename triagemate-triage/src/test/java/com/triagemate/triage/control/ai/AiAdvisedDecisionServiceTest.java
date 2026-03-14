package com.triagemate.triage.control.ai;

import com.triagemate.triage.control.decision.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AiAdvisedDecisionServiceTest {

    private AiAdvisedDecisionService service;
    private StubDecisionService stubDelegate;
    private StubAiAdvisor stubAdvisor;
    private AiAdvisoryProperties properties;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        properties = new AiAdvisoryProperties(
                true, "test",
                Set.of("DEVICE_ERROR", "NORMAL"),
                new AiAdvisoryProperties.Timeouts(Duration.ofSeconds(5)),
                new AiAdvisoryProperties.Cost(0.05, 100.0),
                new AiAdvisoryProperties.Validation(0.70, 0.85)
        );

        stubDelegate = new StubDecisionService();
        stubAdvisor = new StubAiAdvisor();

        CircuitBreaker cb = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
                .circuitBreaker("test");
        Retry retry = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(1).build()).retry("test");

        service = new AiAdvisedDecisionService(
                stubDelegate,
                stubAdvisor,
                new AiAdviceValidator(properties),
                new StubAuditService(),
                new AiCostTracker(properties, meterRegistry),
                new AiMetrics(meterRegistry),
                cb, retry,
                Executors.newSingleThreadExecutor(),
                properties
        );
    }

    @Test
    void happyPath_aiAdviceEnrichesDecision() {
        stubAdvisor.setAdvice(new AiDecisionAdvice(
                "DEVICE_ERROR", 0.92, "High confidence match", true,
                "test", "model", "v1", "1.0.0", "hash",
                10, 20, 0.001, 50
        ));

        DecisionResult result = service.decide(createContext());

        assertEquals(DecisionOutcome.ACCEPT, result.outcome());
        assertEquals(true, result.attributes().get("aiAdvicePresent"));
        assertEquals(true, result.attributes().get("aiAdviceAccepted"));
        assertEquals("ACCEPTED", result.attributes().get("aiAdviceStatus"));
        assertEquals(0.92, result.attributes().get("aiConfidence"));
        assertEquals("v1", result.attributes().get("aiModelVersion"));
        assertEquals("1.0.0", result.attributes().get("aiPromptVersion"));
    }

    @Test
    void aiFailure_fallsBackToDeterministic() {
        stubAdvisor.setThrowException(new TransientAiException("network error"));

        DecisionResult result = service.decide(createContext());

        assertEquals(DecisionOutcome.ACCEPT, result.outcome());
        assertEquals(false, result.attributes().get("aiAdvicePresent"));
    }

    @Test
    void lowConfidence_adviceRejected() {
        stubAdvisor.setAdvice(new AiDecisionAdvice(
                "DEVICE_ERROR", 0.50, "Low confidence", false,
                "test", "model", "v1", "1.0.0", "hash",
                10, 20, 0.001, 50
        ));

        DecisionResult result = service.decide(createContext());

        assertEquals(DecisionOutcome.ACCEPT, result.outcome());
        assertEquals("REJECTED", result.attributes().get("aiAdviceStatus"));
        assertEquals(false, result.attributes().get("aiAdviceAccepted"));
        assertTrue(result.attributes().containsKey("aiAdviceRejectedReason"));
    }

    @Test
    void deterministicResultPreserved() {
        stubAdvisor.setAdvice(AiDecisionAdvice.NONE);

        DecisionResult result = service.decide(createContext());

        assertEquals(DecisionOutcome.ACCEPT, result.outcome());
        assertEquals("deterministic-test", result.reason());
    }

    @Test
    void timeout_fallsBackToDeterministic() {
        AiAdvisoryProperties timeoutProperties = new AiAdvisoryProperties(
                true, "test",
                Set.of("DEVICE_ERROR", "NORMAL"),
                new AiAdvisoryProperties.Timeouts(Duration.ofMillis(20)),
                new AiAdvisoryProperties.Cost(0.05, 100.0),
                new AiAdvisoryProperties.Validation(0.70, 0.85)
        );

        StubAiAdvisor slowAdvisor = new StubAiAdvisor() {
            @Override
            public AiDecisionAdvice advise(DecisionContext<?> context, DecisionResult deterministicResult) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return super.advise(context, deterministicResult);
            }
        };
        slowAdvisor.setAdvice(new AiDecisionAdvice(
                "DEVICE_ERROR", 0.92, "High confidence match", true,
                "test", "model", "v1", "1.0.0", "hash",
                10, 20, 0.001, 50
        ));

        AiAdvisedDecisionService timeoutService = new AiAdvisedDecisionService(
                stubDelegate,
                slowAdvisor,
                new AiAdviceValidator(timeoutProperties),
                new StubAuditService(),
                new AiCostTracker(timeoutProperties, meterRegistry),
                new AiMetrics(meterRegistry),
                CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()).circuitBreaker("timeout"),
                RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build()).retry("timeout"),
                Executors.newSingleThreadExecutor(),
                timeoutProperties
        );

        DecisionResult result = timeoutService.decide(createContext());

        assertEquals(DecisionOutcome.ACCEPT, result.outcome());
        assertEquals(false, result.attributes().get("aiAdvicePresent"));
        assertEquals(false, result.attributes().get("aiAdviceAccepted"));
        assertEquals("NO_ADVICE", result.attributes().get("aiAdviceStatus"));
    }

    @Test
    void budgetExceeded_fallsBackToDeterministic() {
        // Exhaust daily budget so the next call triggers BudgetExceededException
        AiAdvisoryProperties tightBudgetProps = new AiAdvisoryProperties(
                true, "test",
                Set.of("DEVICE_ERROR", "NORMAL"),
                new AiAdvisoryProperties.Timeouts(Duration.ofSeconds(5)),
                new AiAdvisoryProperties.Cost(0.05, 0.01), // daily budget = $0.01
                new AiAdvisoryProperties.Validation(0.70, 0.85)
        );

        AiCostTracker costTracker = new AiCostTracker(tightBudgetProps, meterRegistry);
        costTracker.recordCost(0.02); // exceed the $0.01 daily budget

        stubAdvisor.setAdvice(new AiDecisionAdvice(
                "DEVICE_ERROR", 0.92, "Should not be used", true,
                "test", "model", "v1", "1.0.0", "hash",
                10, 20, 0.001, 50
        ));

        AiAdvisedDecisionService budgetService = new AiAdvisedDecisionService(
                stubDelegate,
                stubAdvisor,
                new AiAdviceValidator(tightBudgetProps),
                new StubAuditService(),
                costTracker,
                new AiMetrics(meterRegistry),
                CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()).circuitBreaker("budget"),
                RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build()).retry("budget"),
                Executors.newSingleThreadExecutor(),
                tightBudgetProps
        );

        DecisionResult result = budgetService.decide(createContext());

        assertEquals(DecisionOutcome.ACCEPT, result.outcome());
        assertEquals(false, result.attributes().get("aiAdvicePresent"));
        assertEquals("NO_ADVICE", result.attributes().get("aiAdviceStatus"));
    }

    @Test
    void transientError_retriedUntilSuccess() {
        AtomicInteger attempts = new AtomicInteger();
        AiDecisionAdvisor flakyAdvisor = (context, deterministicResult) -> {
            int current = attempts.incrementAndGet();
            if (current < 3) {
                throw new TransientAiException("temporary failure " + current);
            }
            return new AiDecisionAdvice(
                    "DEVICE_ERROR", 0.90, "Recovered after retries", true,
                    "test", "model", "v1", "1.0.0", "hash",
                    10, 20, 0.001, 50
            );
        };

        Retry retry = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(3)
                .retryOnException(e -> e instanceof TransientAiException)
                .build()).retry("retry-transient");

        AiAdvisedDecisionService retryService = new AiAdvisedDecisionService(
                stubDelegate,
                flakyAdvisor,
                new AiAdviceValidator(properties),
                new StubAuditService(),
                new AiCostTracker(properties, meterRegistry),
                new AiMetrics(meterRegistry),
                CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()).circuitBreaker("retry-transient"),
                retry,
                Executors.newSingleThreadExecutor(),
                properties
        );

        DecisionResult result = retryService.decide(createContext());

        assertEquals(3, attempts.get());
        assertEquals("ACCEPTED", result.attributes().get("aiAdviceStatus"));
        assertEquals(true, result.attributes().get("aiAdviceAccepted"));
    }

    @Test
    void permanentError_isNotRetriedAndFallsBack() {
        AtomicInteger attempts = new AtomicInteger();
        AiDecisionAdvisor permanentFailureAdvisor = (context, deterministicResult) -> {
            attempts.incrementAndGet();
            throw new PermanentAiException("invalid request");
        };

        Retry retry = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(3)
                .retryOnException(e -> e instanceof TransientAiException)
                .ignoreExceptions(PermanentAiException.class)
                .build()).retry("retry-permanent");

        AiAdvisedDecisionService retryService = new AiAdvisedDecisionService(
                stubDelegate,
                permanentFailureAdvisor,
                new AiAdviceValidator(properties),
                new StubAuditService(),
                new AiCostTracker(properties, meterRegistry),
                new AiMetrics(meterRegistry),
                CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()).circuitBreaker("retry-permanent"),
                retry,
                Executors.newSingleThreadExecutor(),
                properties
        );

        DecisionResult result = retryService.decide(createContext());

        assertEquals(1, attempts.get());
        assertEquals("NO_ADVICE", result.attributes().get("aiAdviceStatus"));
    }

    @Test
    void queueFull_rejectedExecutionFallsBack() {
        Executor rejectingExecutor = command -> {
            throw new java.util.concurrent.RejectedExecutionException("queue full");
        };

        AiAdvisedDecisionService rejectingService = new AiAdvisedDecisionService(
                stubDelegate,
                stubAdvisor,
                new AiAdviceValidator(properties),
                new StubAuditService(),
                new AiCostTracker(properties, meterRegistry),
                new AiMetrics(meterRegistry),
                CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()).circuitBreaker("queue-full"),
                RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build()).retry("queue-full"),
                rejectingExecutor,
                properties
        );

        DecisionResult result = rejectingService.decide(createContext());

        assertEquals(DecisionOutcome.ACCEPT, result.outcome());
        assertEquals(false, result.attributes().get("aiAdvicePresent"));
        assertEquals("NO_ADVICE", result.attributes().get("aiAdviceStatus"));
    }

    private DecisionContext<?> createContext() {
        return DecisionContext.of(
                "evt-123", "device.telemetry", 1,
                Instant.now(), Map.of(), "test-payload"
        );
    }

    // Stubs

    static class StubDecisionService implements DecisionService {
        @Override
        public DecisionResult decide(DecisionContext<?> context) {
            return DecisionResult.of(
                    DecisionOutcome.ACCEPT, "deterministic-test",
                    Map.of("strategy", "rules-v1"),
                    ReasonCode.ACCEPTED_BY_DEFAULT, "All policies passed"
            );
        }
    }

    static class StubAiAdvisor implements AiDecisionAdvisor {
        private AiDecisionAdvice advice = AiDecisionAdvice.NONE;
        private RuntimeException throwException;

        void setAdvice(AiDecisionAdvice advice) { this.advice = advice; }
        void setThrowException(RuntimeException e) { this.throwException = e; }

        @Override
        public AiDecisionAdvice advise(DecisionContext<?> context, DecisionResult deterministicResult) {
            if (throwException != null) throw throwException;
            return advice;
        }
    }

    static class StubAuditService extends AiAuditService {
        StubAuditService() { super(record -> {}); }

        @Override
        public void record(DecisionContext<?> context, DecisionResult deterministicResult,
                           AiDecisionAdvice advice, ValidatedAdvice validated) {
            // no-op for tests
        }

        @Override
        public void recordError(DecisionContext<?> context, DecisionResult deterministicResult, String errorType, String errorMessage) {
            recordError(context, errorType, errorMessage);
        }

        @Override
        public void recordError(DecisionContext<?> context, String errorType, String errorMessage) {
            // no-op for tests
        }
    }
}
