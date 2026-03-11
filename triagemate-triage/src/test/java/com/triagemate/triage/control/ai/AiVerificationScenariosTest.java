package com.triagemate.triage.control.ai;

import com.triagemate.triage.control.decision.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 12A Verification Scenarios V1–V6.
 *
 * These tests map 1:1 to the acceptance verification steps defined in
 * docs/coding-process/V1/phase-12/phase12A.md (lines 686-736).
 */
class AiVerificationScenariosTest {

    private SimpleMeterRegistry meterRegistry;
    private AiAdvisoryProperties properties;
    private TestAiAuditService auditService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        properties = new AiAdvisoryProperties(
                true, "test", null, null,
                Set.of("DEVICE_ERROR", "NORMAL"),
                new AiAdvisoryProperties.Timeouts(Duration.ofSeconds(5)),
                new AiAdvisoryProperties.Cost(0.05, 100.0),
                new AiAdvisoryProperties.Validation(0.70, 0.85)
        );
        auditService = new TestAiAuditService();
    }

    // ──────────────────────────────────────────────────────────────
    // V1 — AI Advisory Happy Path
    // ──────────────────────────────────────────────────────────────

    @Nested
    class V1_HappyPath {

        @Test
        void fullHappyPath_aiAdviceAccepted_metricsAndAuditRecorded() {
            AiDecisionAdvice advice = new AiDecisionAdvice(
                    "DEVICE_ERROR", 0.90, "High confidence match", true,
                    "anthropic", "claude", "sonnet-4", "1.0.1", "hash123",
                    12, 25, 0.003, 145
            );

            AiAdvisedDecisionService service = buildService(
                    (ctx, det) -> advice,
                    CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()).circuitBreaker("v1"),
                    RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build()).retry("v1")
            );

            // Step 1-2: Send event, deterministic decision produced
            DecisionResult result = service.decide(createContext());

            // Step 3-4: AI advisor called, validator accepts
            assertEquals(DecisionOutcome.ACCEPT, result.outcome());
            assertEquals(true, result.attributes().get("aiAdvicePresent"));
            assertEquals(true, result.attributes().get("aiAdviceAccepted"));
            assertEquals("ACCEPTED", result.attributes().get("aiAdviceStatus"));

            // Step 5: Final decision includes AI metadata and override
            assertEquals(0.90, result.attributes().get("aiConfidence"));
            assertEquals("sonnet-4", result.attributes().get("aiModelVersion"));
            assertEquals("1.0.1", result.attributes().get("aiPromptVersion"));
            assertEquals(true, result.attributes().get("aiOverrideApplied"));
            assertEquals("ACCEPT", result.attributes().get("originalOutcome"));
            assertEquals("DEVICE_ERROR", result.reason());
            assertEquals("AI override: High confidence match", result.humanReadableReason());

            // Step 6: Audit trail records full interaction
            assertTrue(auditService.recordCalled, "Audit service record() should be called");
            assertFalse(auditService.errorCalled, "Audit error should NOT be called on happy path");

            // Step 7: Prometheus metrics updated
            double callCount = countMetric("triagemate.ai.calls.total", "status", "success");
            assertTrue(callCount >= 1, "Call counter should be incremented");

            double acceptedCount = countMetric("triagemate.ai.advice.accepted.total");
            assertTrue(acceptedCount >= 1, "Accepted counter should be incremented");

            double costTotal = countMetric("triagemate.ai.cost.usd.total");
            assertTrue(costTotal > 0, "Cost metric should be recorded");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // V2 — AI Fallback on Timeout
    // ──────────────────────────────────────────────────────────────

    @Nested
    class V2_TimeoutFallback {

        @Test
        void timeout_fallsBackAndRecordsAuditAndMetrics() {
            AiAdvisoryProperties shortTimeout = new AiAdvisoryProperties(
                    true, "test", null, null,
                    Set.of("DEVICE_ERROR", "NORMAL"),
                    new AiAdvisoryProperties.Timeouts(Duration.ofMillis(20)),
                    new AiAdvisoryProperties.Cost(0.05, 100.0),
                    new AiAdvisoryProperties.Validation(0.70, 0.85)
            );

            AiDecisionAdvisor slowAdvisor = (ctx, det) -> {
                try { Thread.sleep(500); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new AiDecisionAdvice(
                        "DEVICE_ERROR", 0.95, "late", true,
                        "test", "model", "v1", "1.0.1", "hash",
                        0, 0, 0.0, 500
                );
            };

            AiAdvisedDecisionService service = new AiAdvisedDecisionService(
                    new StubDecisionService(), slowAdvisor,
                    new AiAdviceValidator(shortTimeout),
                    auditService,
                    new AiCostTracker(shortTimeout, meterRegistry),
                    new AiMetrics(meterRegistry),
                    CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()).circuitBreaker("v2"),
                    RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build()).retry("v2"),
                    Executors.newSingleThreadExecutor(),
                    shortTimeout
            );

            // Step 1-2: Mock AI delays > timeout
            DecisionResult result = service.decide(createContext());

            // Step 3: Deterministic decision returned unchanged
            assertEquals(DecisionOutcome.ACCEPT, result.outcome());
            assertEquals(false, result.attributes().get("aiAdvicePresent"));

            // Step 4: Audit trail records timeout error
            assertTrue(auditService.errorCalled, "Audit recordError should be called on timeout");
            assertEquals("TIMEOUT", auditService.lastErrorType);

            // Step 5: Fallback metric incremented
            double timeoutCount = countMetric("triagemate.ai.fallback.total", "reason", "timeout");
            assertTrue(timeoutCount >= 1, "Timeout fallback metric should be incremented");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // V3 — AI Fallback on Low Confidence
    // ──────────────────────────────────────────────────────────────

    @Nested
    class V3_LowConfidenceFallback {

        @Test
        void lowConfidence_rejectedAndAuditRecordsReason() {
            AiDecisionAdvice lowConfAdvice = new AiDecisionAdvice(
                    "DEVICE_ERROR", 0.50, "Very uncertain", false,
                    "test", "model", "v1", "1.0.1", "hash",
                    10, 15, 0.001, 80
            );

            AiAdvisedDecisionService service = buildService(
                    (ctx, det) -> lowConfAdvice,
                    CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()).circuitBreaker("v3"),
                    RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build()).retry("v3")
            );

            // Step 1: AI returns confidence 0.50
            DecisionResult result = service.decide(createContext());

            // Step 2: Validator rejects advice (< 0.70 threshold)
            assertEquals("REJECTED", result.attributes().get("aiAdviceStatus"));
            assertEquals(false, result.attributes().get("aiAdviceAccepted"));

            // Step 3: Deterministic decision returned unchanged
            assertEquals(DecisionOutcome.ACCEPT, result.outcome());

            // Step 4: Audit trail records rejection with reason
            assertTrue(auditService.recordCalled, "Audit record should be called even for rejected advice");
            assertTrue(result.attributes().containsKey("aiAdviceRejectedReason"));

            // Rejection metric
            double rejectedCount = countMetric("triagemate.ai.advice.rejected.total");
            assertTrue(rejectedCount >= 1, "Rejected counter should be incremented");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // V4 — Circuit Breaker
    // ──────────────────────────────────────────────────────────────

    @Nested
    class V4_CircuitBreaker {

        @Test
        void circuitBreaker_fullLifecycle_withHealthState() {
            CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                    .failureRateThreshold(50)
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(4)
                    .minimumNumberOfCalls(4)
                    .waitDurationInOpenState(Duration.ofMillis(100))
                    .permittedNumberOfCallsInHalfOpenState(1)
                    .recordExceptions(TransientAiException.class)
                    .ignoreExceptions(PermanentAiException.class, BudgetExceededException.class)
                    .build();
            CircuitBreaker cb = CircuitBreakerRegistry.of(cbConfig).circuitBreaker("v4");

            AiMetrics metrics = new AiMetrics(meterRegistry);
            metrics.registerCircuitBreakerStateGauge(cb, "test");

            AtomicReference<RuntimeException> throwRef = new AtomicReference<>(new TransientAiException("fail"));
            AiDecisionAdvisor advisor = (ctx, det) -> {
                RuntimeException ex = throwRef.get();
                if (ex != null) throw ex;
                return new AiDecisionAdvice(
                        "DEVICE_ERROR", 0.92, "recovered", true,
                        "test", "model", "v1", "1.0.1", "hash",
                        10, 20, 0.001, 50
                );
            };

            AiAdvisedDecisionService service = new AiAdvisedDecisionService(
                    new StubDecisionService(), advisor,
                    new AiAdviceValidator(properties),
                    auditService,
                    new AiCostTracker(properties, meterRegistry),
                    metrics, cb,
                    RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build()).retry("v4"),
                    Executors.newSingleThreadExecutor(),
                    properties
            );

            // Step 1: 4 consecutive failures
            for (int i = 0; i < 4; i++) {
                service.decide(createContext());
            }

            // Step 2: Circuit breaker opens
            assertEquals(CircuitBreaker.State.OPEN, cb.getState());

            // Health endpoint reflects OPEN
            AiHealthIndicator health = new AiHealthIndicator(cb, properties);
            assertEquals("DOWN", health.health().getStatus().getCode());

            // Gauge reflects OPEN
            Gauge gauge = meterRegistry.find("triagemate.ai.circuit.breaker.state")
                    .tag("provider", "test").gauge();
            assertNotNull(gauge);
            assertEquals(1.0, gauge.value(), "Gauge should show OPEN (1)");

            // Step 3: Next calls fast-fail
            DecisionResult fastFail = service.decide(createContext());
            assertEquals(false, fastFail.attributes().get("aiAdvicePresent"));

            // Step 4: Transition to half-open
            cb.transitionToHalfOpenState();
            assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState());
            assertEquals("UP", health.health().getStatus().getCode());

            // Step 5: Successful call closes breaker
            throwRef.set(null);
            DecisionResult recovered = service.decide(createContext());
            assertEquals(true, recovered.attributes().get("aiAdvicePresent"));
            assertEquals(CircuitBreaker.State.CLOSED, cb.getState());

            // Step 6: Health endpoint reflects CLOSED
            assertEquals("UP", health.health().getStatus().getCode());
            assertEquals(0.0, gauge.value(), "Gauge should show CLOSED (0)");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // V5 — Cost Budget Exceeded
    // ──────────────────────────────────────────────────────────────

    @Nested
    class V5_BudgetExceeded {

        @Test
        void budgetExceeded_fallbackAndMetrics() {
            // Step 1: daily budget = $0.01
            AiAdvisoryProperties tightBudget = new AiAdvisoryProperties(
                    true, "test", null, null,
                    Set.of("DEVICE_ERROR", "NORMAL"),
                    new AiAdvisoryProperties.Timeouts(Duration.ofSeconds(5)),
                    new AiAdvisoryProperties.Cost(0.05, 0.01, 0.05),
                    new AiAdvisoryProperties.Validation(0.70, 0.85)
            );

            AiCostTracker costTracker = new AiCostTracker(tightBudget, meterRegistry);
            costTracker.recordCost(0.02); // exhaust budget

            AiDecisionAdvice advice = new AiDecisionAdvice(
                    "DEVICE_ERROR", 0.92, "should not be used", true,
                    "test", "model", "v1", "1.0.1", "hash",
                    10, 20, 0.001, 50
            );

            AiAdvisedDecisionService service = new AiAdvisedDecisionService(
                    new StubDecisionService(), (ctx, det) -> advice,
                    new AiAdviceValidator(tightBudget),
                    auditService, costTracker,
                    new AiMetrics(meterRegistry),
                    CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()).circuitBreaker("v5"),
                    RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build()).retry("v5"),
                    Executors.newSingleThreadExecutor(),
                    tightBudget
            );

            // Step 2-3: AI calls return NONE
            DecisionResult result = service.decide(createContext());
            assertEquals(false, result.attributes().get("aiAdvicePresent"));
            assertEquals("NO_ADVICE", result.attributes().get("aiAdviceStatus"));

            // Step 4: Budget exceeded counter incremented
            double budgetExceeded = countMetric("triagemate.ai.budget.exceeded.total");
            assertTrue(budgetExceeded >= 1, "Budget exceeded counter should be incremented");

            double fallbackCount = countMetric("triagemate.ai.fallback.total", "reason", "budget_exceeded");
            assertTrue(fallbackCount >= 1, "Budget exceeded fallback metric should be incremented");

            // Step 5: Deterministic decisions continue unaffected
            assertEquals(DecisionOutcome.ACCEPT, result.outcome());
            assertEquals("deterministic-test", result.reason());

            // Audit records the budget error
            assertTrue(auditService.errorCalled);
            assertEquals("BUDGET_EXCEEDED", auditService.lastErrorType);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // V6 — AI Disabled
    // ──────────────────────────────────────────────────────────────

    @Nested
    class V6_AiDisabled {

        @Test
        void aiDisabled_deterministicDecisionHasNoAiAttributes() {
            // When triagemate.ai.enabled=false:
            // - AiAdvisoryConfig is NOT loaded (@ConditionalOnProperty)
            // - AiResilienceConfig is NOT loaded
            // - AiExecutorConfig is NOT loaded
            // - Only DefaultDecisionService bean exists (no @Primary decorator)
            //
            // We verify the contract: the StubDecisionService (standing in for
            // DefaultDecisionService) produces results with no AI attributes.
            // This confirms the decorator is the ONLY source of AI metadata.

            StubDecisionService rawService = new StubDecisionService();
            DecisionResult result = rawService.decide(createContext());

            assertEquals(DecisionOutcome.ACCEPT, result.outcome());
            assertFalse(result.attributes().containsKey("aiAdvicePresent"),
                    "Without AI decorator, no AI attributes should be present");
            assertFalse(result.attributes().containsKey("aiAdviceStatus"),
                    "Without AI decorator, no AI status should be present");
            assertFalse(result.attributes().containsKey("aiConfidence"),
                    "Without AI decorator, no AI confidence should be present");
        }

        @Test
        void aiConfigBeans_areConditionalOnEnabled() {
            // Verify the conditional annotations are correctly set
            // AiAdvisoryConfig, AiResilienceConfig, AiExecutorConfig all require
            // triagemate.ai.enabled=true
            assertTrue(
                    AiAdvisoryConfig.class.isAnnotationPresent(
                            org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class),
                    "AiAdvisoryConfig must be conditional on triagemate.ai.enabled");

            assertTrue(
                    AiResilienceConfig.class.isAnnotationPresent(
                            org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class),
                    "AiResilienceConfig must be conditional on triagemate.ai.enabled");

            assertTrue(
                    AiExecutorConfig.class.isAnnotationPresent(
                            org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class),
                    "AiExecutorConfig must be conditional on triagemate.ai.enabled");

            assertTrue(
                    AiHealthIndicator.class.isAnnotationPresent(
                            org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class),
                    "AiHealthIndicator must be conditional on triagemate.ai.enabled");

            assertTrue(
                    AiCostResetScheduler.class.isAnnotationPresent(
                            org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class),
                    "AiCostResetScheduler must be conditional on triagemate.ai.enabled");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    private AiAdvisedDecisionService buildService(
            AiDecisionAdvisor advisor, CircuitBreaker cb,
            io.github.resilience4j.retry.Retry retry) {
        return new AiAdvisedDecisionService(
                new StubDecisionService(), advisor,
                new AiAdviceValidator(properties),
                auditService,
                new AiCostTracker(properties, meterRegistry),
                new AiMetrics(meterRegistry),
                cb, retry,
                Executors.newSingleThreadExecutor(),
                properties
        );
    }

    private DecisionContext<?> createContext() {
        return DecisionContext.of(
                "evt-v-123", "device.telemetry", 1,
                Instant.now(), Map.of(), "test-payload"
        );
    }

    private double countMetric(String name) {
        Counter counter = meterRegistry.find(name).counter();
        return counter != null ? counter.count() : 0.0;
    }

    private double countMetric(String name, String tagKey, String tagValue) {
        Counter counter = meterRegistry.find(name).tag(tagKey, tagValue).counter();
        return counter != null ? counter.count() : 0.0;
    }

    static class StubDecisionService implements DecisionService {
        @Override
        public DecisionResult decide(DecisionContext<?> context) {
            return DecisionResult.of(
                    DecisionOutcome.ACCEPT, "deterministic-test",
                    Map.of("decisionId", "dec-stub-" + context.eventId(), "strategy", "rules-v1"),
                    ReasonCode.ACCEPTED_BY_DEFAULT, "All policies passed"
            );
        }
    }

}
