package com.triagemate.triage.control.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionOutcome;
import com.triagemate.triage.control.decision.DecisionResult;
import com.triagemate.triage.control.decision.ReasonCode;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AiAdvisoryScenariosTest {

    @Test
    void allowlistStrict_acceptsAdviceWhenClassificationIsAllowed() {
        AiAdviceValidator validator = new AiAdviceValidator(new AiAdvisoryProperties(
                true,
                "test", null, null,
                Set.of("DEVICE_ERROR", "NORMAL"),
                new AiAdvisoryProperties.Timeouts(Duration.ofSeconds(5)),
                new AiAdvisoryProperties.Cost(0.05, 100.0),
                new AiAdvisoryProperties.Validation(0.70, 0.85)
        ));

        AiDecisionAdvice advice = new AiDecisionAdvice(
                "DEVICE_ERROR", 0.90, "Looks like a device issue", true,
                "test", "model", "v1", "1.0.1", "hash",
                0, 0, 0.0, 10
        );

        ValidatedAdvice result = validator.validate(deterministicResult(), advice);

        assertEquals(ValidatedAdvice.Status.ACCEPTED, result.status());
    }

    @Test
    void allowlistStrict_rejectsAdviceWhenClassificationIsNotAllowed() {
        AiAdviceValidator validator = new AiAdviceValidator(new AiAdvisoryProperties(
                true, "test", null, null,
                Set.of("DEVICE_ERROR", "NORMAL"),
                new AiAdvisoryProperties.Timeouts(Duration.ofSeconds(5)),
                new AiAdvisoryProperties.Cost(0.05, 100.0),
                new AiAdvisoryProperties.Validation(0.70, 0.85)
        ));

        AiDecisionAdvice advice = new AiDecisionAdvice(
                "ACCEPT", 0.90, "Generic decision label", true,
                "test", "model", "v1", "1.0.1", "hash",
                0, 0, 0.0, 10
        );

        ValidatedAdvice result = validator.validate(deterministicResult(), advice);

        assertEquals(ValidatedAdvice.Status.REJECTED, result.status());
    }

    @Test
    void timeoutFallback_returnsDeterministicResultWithoutAiAdvice() {
        AiAdvisoryProperties timeoutProperties = new AiAdvisoryProperties(
                true, "test", null, null,
                Set.of("DEVICE_ERROR", "NORMAL"),
                new AiAdvisoryProperties.Timeouts(Duration.ofMillis(20)),
                new AiAdvisoryProperties.Cost(0.05, 100.0),
                new AiAdvisoryProperties.Validation(0.70, 0.85)
        );

        AiAdvisedDecisionService service = new AiAdvisedDecisionService(
                context -> DecisionResult.of(
                        DecisionOutcome.ACCEPT,
                        "deterministic",
                        Map.of("decisionId", "dec-stub", "strategy", "rules-v1"),
                        ReasonCode.ACCEPTED_BY_DEFAULT,
                        "All policies passed"
                ),
                (context, deterministicResult) -> {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return new AiDecisionAdvice(
                            "DEVICE_ERROR", 0.95, "slow response", true,
                            "test", "model", "v1", "1.0.1", "hash",
                            0, 0, 0.0, 500
                    );
                },
                new AiAdviceValidator(timeoutProperties),
                new TestAiAuditService(),
                new AiCostTracker(timeoutProperties, new SimpleMeterRegistry()),
                new AiMetrics(new SimpleMeterRegistry()),
                CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()).circuitBreaker("timeout-scenario"),
                RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build()).retry("timeout-scenario"),
                Executors.newSingleThreadExecutor(),
                timeoutProperties
        );

        DecisionResult result = service.decide(testContext());

        assertEquals(DecisionOutcome.ACCEPT, result.outcome());
        assertFalse((Boolean) result.attributes().get("aiAdvicePresent"));
        assertEquals("NO_ADVICE", result.attributes().get("aiAdviceStatus"));
    }

    @Test
    void parser_extractsNestedJsonFromMixedText() {
        AiResponseParser parser = new AiResponseParser(new ObjectMapper());
        String response = """
                Here is my advisory input:
                {
                  "suggestedClassification": "DEVICE_ERROR",
                  "confidence": 0.88,
                  "reasoning": "Nested details should not break extraction",
                  "recommendsOverride": true,
                  "details": {"source": "llama", "metadata": {"attempt": 1}}
                }
                Additional text after JSON.
                """;

        AiClassificationResponse parsed = parser.parse(response, Set.of("DEVICE_ERROR", "NORMAL"));

        assertEquals("DEVICE_ERROR", parsed.suggestedClassification());
        assertEquals(0.88, parsed.confidence(), 0.001);
    }


    private DecisionResult deterministicResult() {
        return DecisionResult.of(
                DecisionOutcome.ACCEPT,
                "deterministic",
                Map.of("decisionId", "dec-stub", "strategy", "rules-v1"),
                ReasonCode.ACCEPTED_BY_DEFAULT,
                "All policies passed"
        );
    }

    private DecisionContext<?> testContext() {
        return DecisionContext.of(
                "evt-001",
                "triagemate.ingest.input-received",
                1,
                Instant.now(),
                Map.of(),
                "payload"
        );
    }

}
