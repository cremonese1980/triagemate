package com.triagemate.triage.control.ai;

import com.triagemate.testsupport.JdbcIntegrationTestBase;
import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionOutcome;
import com.triagemate.triage.control.decision.DecisionResult;
import com.triagemate.triage.control.decision.DecisionService;
import com.triagemate.triage.control.decision.ReasonCode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M9 — Budget exceeded fallback (integration test).
 *
 * Verifies that when cost guardrails are exceeded:
 * 1. Deterministic fallback is returned unchanged.
 * 2. Budget-exceeded metric is incremented.
 * 3. An error audit row with error_type=BUDGET_EXCEEDED is persisted to the database.
 */
@Import({JdbcAiAuditRepository.class, AiAuditService.class})
class BudgetExceededFallbackIT extends JdbcIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AiAuditService auditService;

    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void cleanAuditTable() {
        jdbcTemplate.update("DELETE FROM ai_decision_audit");
        meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    void dailyBudgetExceeded_fallsBackAndRecordsAuditRow() {
        // Tight daily budget: $0.01 daily, $0.05 per-decision, estimate $0.005
        AiAdvisoryProperties props = new AiAdvisoryProperties(
                true, "test-provider", null, null,
                Set.of("DEVICE_ERROR", "NORMAL"),
                new AiAdvisoryProperties.Timeouts(Duration.ofSeconds(5)),
                new AiAdvisoryProperties.Cost(0.05, 0.01, 0.005),
                new AiAdvisoryProperties.Validation(0.70, 0.85)
        );

        AiCostTracker costTracker = new AiCostTracker(props, meterRegistry);
        // Exhaust daily budget
        costTracker.recordCost(0.009);

        AiAdvisedDecisionService service = buildService(props, costTracker, alwaysSuccessAdvisor());

        DecisionResult result = service.decide(createContext("evt-m9-daily"));

        // Deterministic fallback
        assertThat(result.outcome()).isEqualTo(DecisionOutcome.ACCEPT);
        assertThat(result.attributes().get("aiAdvicePresent")).isEqualTo(false);
        assertThat(result.attributes().get("aiAdviceStatus")).isEqualTo("NO_ADVICE");

        // Budget exceeded metric
        double budgetExceeded = meterRegistry.counter("triagemate.ai.budget.exceeded.total").count();
        assertThat(budgetExceeded).isGreaterThanOrEqualTo(1.0);

        // Fallback metric
        double fallbackCount = meterRegistry.counter("triagemate.ai.fallback.total", "reason", "budget_exceeded").count();
        assertThat(fallbackCount).isEqualTo(1.0);

        // Audit error row in database
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT event_id, error_type, error_message FROM ai_decision_audit WHERE event_id = ?",
                "evt-m9-daily"
        );
        assertThat(row.get("error_type")).isEqualTo("BUDGET_EXCEEDED");
        assertThat((String) row.get("error_message")).contains("Daily cost limit exceeded");
    }

    @Test
    void perDecisionBudgetExceeded_fallsBackAndRecordsAuditRow() {
        // Estimate ($0.10) exceeds per-decision limit ($0.05)
        AiAdvisoryProperties props = new AiAdvisoryProperties(
                true, "test-provider", null, null,
                Set.of("DEVICE_ERROR", "NORMAL"),
                new AiAdvisoryProperties.Timeouts(Duration.ofSeconds(5)),
                new AiAdvisoryProperties.Cost(0.05, 100.0, 0.10),
                new AiAdvisoryProperties.Validation(0.70, 0.85)
        );

        AiCostTracker costTracker = new AiCostTracker(props, meterRegistry);
        AiAdvisedDecisionService service = buildService(props, costTracker, alwaysSuccessAdvisor());

        DecisionResult result = service.decide(createContext("evt-m9-perdec"));

        // Deterministic fallback
        assertThat(result.outcome()).isEqualTo(DecisionOutcome.ACCEPT);
        assertThat(result.attributes().get("aiAdvicePresent")).isEqualTo(false);

        // No cost recorded (blocked pre-call)
        assertThat(costTracker.getDailyCostUsd()).isEqualTo(0.0);

        // Audit error row
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT event_id, error_type, error_message FROM ai_decision_audit WHERE event_id = ?",
                "evt-m9-perdec"
        );
        assertThat(row.get("error_type")).isEqualTo("BUDGET_EXCEEDED");
        assertThat((String) row.get("error_message")).contains("Per-decision cost limit exceeded");
    }

    private AiAdvisedDecisionService buildService(
            AiAdvisoryProperties props,
            AiCostTracker costTracker,
            AiDecisionAdvisor advisor
    ) {
        CircuitBreaker cb = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
                .circuitBreaker("m9-budget-" + System.nanoTime());
        Retry retry = RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build())
                .retry("m9-budget-" + System.nanoTime());

        return new AiAdvisedDecisionService(
                new DeterministicStub(),
                advisor,
                new AiAdviceValidator(props),
                auditService,
                costTracker,
                new AiMetrics(meterRegistry),
                cb, retry,
                Executors.newSingleThreadExecutor(),
                props
        );
    }

    private AiDecisionAdvisor alwaysSuccessAdvisor() {
        return (context, deterministicResult) -> new AiDecisionAdvice(
                "DEVICE_ERROR", 0.92, "High confidence match", true,
                "test-provider", "model", "v1", "1.0.0", "hash",
                10, 20, 0.001, 50
        );
    }

    private DecisionContext<?> createContext(String eventId) {
        return DecisionContext.of(
                eventId, "triagemate.ingest.input-received", 1,
                Instant.now(), Map.of("requestId", "req-" + eventId), "test-payload"
        );
    }

    static class DeterministicStub implements DecisionService {
        @Override
        public DecisionResult decide(DecisionContext<?> context) {
            return DecisionResult.of(
                    DecisionOutcome.ACCEPT, "deterministic-test",
                    Map.of("decisionId", "dec-" + context.eventId(), "strategy", "rules-v1"),
                    ReasonCode.ACCEPTED_BY_DEFAULT, "All policies passed"
            );
        }
    }
}
