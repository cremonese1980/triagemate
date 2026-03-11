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
 * M11 — Audit completeness (integration test).
 *
 * Verifies that after one success and one error AI interaction,
 * the ai_decision_audit table contains:
 * - success row with provider/model/prompt_version/confidence/latency and event/decision correlation
 * - error row with error_type/error_message and event/decision correlation
 */
@Import({JdbcAiAuditRepository.class, AiAuditService.class})
class AuditCompletenessIT extends JdbcIntegrationTestBase {

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
    void successAudit_containsAllProviderAndModelFields() {
        AiAdvisoryProperties props = defaultProps();
        AiCostTracker costTracker = new AiCostTracker(props, meterRegistry);
        AiAdvisedDecisionService service = buildService(props, costTracker, successAdvisor());

        service.decide(createContext("evt-m11-success", "req-m11-success"));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT decision_id, event_id, provider, model, model_version,
                       prompt_version, prompt_hash, confidence, suggested_classification,
                       recommends_override, accepted_by_validator, reasoning,
                       input_tokens, output_tokens, cost_usd, latency_ms,
                       error_type, error_message
                FROM ai_decision_audit WHERE event_id = ?
                """,
                "evt-m11-success"
        );

        // Correlation fields
        assertThat(row.get("decision_id")).isEqualTo("dec-evt-m11-success");
        assertThat(row.get("event_id")).isEqualTo("evt-m11-success");

        // Provider/model metadata
        assertThat(row.get("provider")).isEqualTo("test-provider");
        assertThat(row.get("model")).isEqualTo("test-model");
        assertThat(row.get("model_version")).isEqualTo("v2.1");
        assertThat(row.get("prompt_version")).isEqualTo("1.0.0");
        assertThat(row.get("prompt_hash")).isEqualTo("sha256abc");

        // Decision data
        assertThat(((Number) row.get("confidence")).doubleValue()).isEqualTo(0.93);
        assertThat(row.get("suggested_classification")).isEqualTo("DEVICE_ERROR");
        assertThat(row.get("recommends_override")).isEqualTo(true);
        assertThat(row.get("accepted_by_validator")).isEqualTo(true);
        assertThat(row.get("reasoning")).isEqualTo("Consistent with telemetry patterns");

        // Token/cost/latency
        assertThat(((Number) row.get("input_tokens")).intValue()).isEqualTo(15);
        assertThat(((Number) row.get("output_tokens")).intValue()).isEqualTo(25);
        assertThat(((Number) row.get("cost_usd")).doubleValue()).isEqualTo(0.004);
        assertThat(((Number) row.get("latency_ms")).longValue()).isEqualTo(120L);

        // No error fields
        assertThat(row.get("error_type")).isNull();
        assertThat(row.get("error_message")).isNull();
    }

    @Test
    void errorAudit_containsErrorTypeAndMessage() {
        // Trigger budget exceeded for error audit row
        AiAdvisoryProperties props = new AiAdvisoryProperties(
                true, "test-provider", null, null,
                Set.of("DEVICE_ERROR", "NORMAL"),
                new AiAdvisoryProperties.Timeouts(Duration.ofSeconds(5)),
                new AiAdvisoryProperties.Cost(0.05, 0.01, 0.005),
                new AiAdvisoryProperties.Validation(0.70, 0.85)
        );

        AiCostTracker costTracker = new AiCostTracker(props, meterRegistry);
        costTracker.recordCost(0.009); // exhaust daily budget

        AiAdvisedDecisionService service = buildService(props, costTracker, successAdvisor());

        service.decide(createContext("evt-m11-error", "req-m11-error"));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT decision_id, event_id, provider, confidence,
                       error_type, error_message
                FROM ai_decision_audit WHERE event_id = ?
                """,
                "evt-m11-error"
        );

        // Correlation fields populated
        assertThat(row.get("decision_id")).isEqualTo("dec-evt-m11-error");
        assertThat(row.get("event_id")).isEqualTo("evt-m11-error");

        // Error fields populated
        assertThat(row.get("error_type")).isEqualTo("BUDGET_EXCEEDED");
        assertThat((String) row.get("error_message")).contains("Daily cost limit exceeded");

        // Provider/confidence NOT populated (error path, no AI call made)
        assertThat(row.get("provider")).isNull();
        assertThat(row.get("confidence")).isNull();
    }

    @Test
    void correlationFields_matchOriginalContext() {
        AiAdvisoryProperties props = defaultProps();
        AiCostTracker costTracker = new AiCostTracker(props, meterRegistry);
        AiAdvisedDecisionService service = buildService(props, costTracker, successAdvisor());

        // Success interaction
        service.decide(createContext("evt-corr-1", "req-corr-1"));

        // Error interaction (timeout via budget exceeded)
        AiAdvisoryProperties tightProps = new AiAdvisoryProperties(
                true, "test-provider", null, null,
                Set.of("DEVICE_ERROR", "NORMAL"),
                new AiAdvisoryProperties.Timeouts(Duration.ofSeconds(5)),
                new AiAdvisoryProperties.Cost(0.05, 0.001, 0.005),
                new AiAdvisoryProperties.Validation(0.70, 0.85)
        );
        AiCostTracker tightTracker = new AiCostTracker(tightProps, meterRegistry);
        AiAdvisedDecisionService errorService = buildService(tightProps, tightTracker, successAdvisor());
        errorService.decide(createContext("evt-corr-2", "req-corr-2"));

        // Verify both rows exist with correct correlation
        int totalRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_decision_audit WHERE event_id IN (?, ?)",
                Integer.class, "evt-corr-1", "evt-corr-2"
        );
        assertThat(totalRows).isEqualTo(2);

        // Success row: decisionId from attributes
        Map<String, Object> successRow = jdbcTemplate.queryForMap(
                "SELECT decision_id, event_id FROM ai_decision_audit WHERE event_id = ?",
                "evt-corr-1"
        );
        assertThat(successRow.get("decision_id")).isEqualTo("dec-evt-corr-1");
        assertThat(successRow.get("event_id")).isEqualTo("evt-corr-1");

        // Error row: decisionId from attributes
        Map<String, Object> errorRow = jdbcTemplate.queryForMap(
                "SELECT decision_id, event_id FROM ai_decision_audit WHERE event_id = ?",
                "evt-corr-2"
        );
        assertThat(errorRow.get("decision_id")).isEqualTo("dec-evt-corr-2");
        assertThat(errorRow.get("event_id")).isEqualTo("evt-corr-2");
    }

    // --- Helpers ---

    private AiAdvisoryProperties defaultProps() {
        return new AiAdvisoryProperties(
                true, "test-provider", null, null,
                Set.of("DEVICE_ERROR", "NORMAL"),
                new AiAdvisoryProperties.Timeouts(Duration.ofSeconds(5)),
                new AiAdvisoryProperties.Cost(0.05, 100.0),
                new AiAdvisoryProperties.Validation(0.70, 0.85)
        );
    }

    private AiDecisionAdvisor successAdvisor() {
        return (context, deterministicResult) -> new AiDecisionAdvice(
                "DEVICE_ERROR", 0.93, "Consistent with telemetry patterns", true,
                "test-provider", "test-model", "v2.1", "1.0.0", "sha256abc",
                15, 25, 0.004, 120
        );
    }

    private AiAdvisedDecisionService buildService(
            AiAdvisoryProperties props,
            AiCostTracker costTracker,
            AiDecisionAdvisor advisor
    ) {
        CircuitBreaker cb = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
                .circuitBreaker("m11-audit-" + System.nanoTime());
        Retry retry = RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build())
                .retry("m11-audit-" + System.nanoTime());

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

    private DecisionContext<?> createContext(String eventId, String requestId) {
        return DecisionContext.of(
                eventId, "triagemate.ingest.input-received", 1,
                Instant.now(), Map.of("requestId", requestId), "test-payload"
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
