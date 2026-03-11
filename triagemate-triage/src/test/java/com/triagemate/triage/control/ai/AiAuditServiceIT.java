package com.triagemate.triage.control.ai;

import com.triagemate.testsupport.JdbcIntegrationTestBase;
import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionOutcome;
import com.triagemate.triage.control.decision.DecisionResult;
import com.triagemate.triage.control.decision.ReasonCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Import({JdbcAiAuditRepository.class, AiAuditService.class})
class AiAuditServiceIT extends JdbcIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AiAuditService aiAuditService;

    @BeforeEach
    void cleanAuditTable() {
        jdbcTemplate.update("DELETE FROM ai_decision_audit");
    }

    @Test
    void record_shouldPersistFullAuditRow() {
        DecisionContext<?> context = DecisionContext.of(
                "evt-audit-1",
                "triagemate.ingest.input-received",
                1,
                Instant.now(),
                Map.of("requestId", "req-audit-1"),
                "payload"
        );

        DecisionResult deterministic = DecisionResult.of(
                DecisionOutcome.ACCEPT,
                "deterministic",
                Map.of("decisionId", "dec-audit-1"),
                ReasonCode.ACCEPTED_BY_DEFAULT,
                "All policies passed"
        );

        AiDecisionAdvice advice = new AiDecisionAdvice(
                "DEVICE_ERROR",
                0.93,
                "Consistent with event features",
                true,
                "anthropic",
                "claude",
                "sonnet-4",
                "1.0.1",
                "hash123",
                11,
                21,
                0.004,
                123
        );

        aiAuditService.record(context, deterministic, advice, ValidatedAdvice.accepted(advice));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT decision_id, event_id, provider, model, model_version,
                       prompt_version, prompt_hash, confidence, suggested_classification,
                       recommends_override, accepted_by_validator, input_tokens, output_tokens,
                       cost_usd, latency_ms, error_type
                FROM ai_decision_audit
                WHERE event_id = ?
                """,
                "evt-audit-1"
        );

        assertThat(row.get("decision_id")).isEqualTo("dec-audit-1");
        assertThat(row.get("provider")).isEqualTo("anthropic");
        assertThat(row.get("suggested_classification")).isEqualTo("DEVICE_ERROR");
        assertThat(row.get("accepted_by_validator")).isEqualTo(true);
        assertThat(((Number) row.get("latency_ms")).longValue()).isEqualTo(123L);
        assertThat(row.get("error_type")).isNull();
    }

    @Test
    void recordError_shouldPersistErrorOnlyAuditRow() {
        DecisionContext<?> context = DecisionContext.of(
                "evt-audit-2",
                "triagemate.ingest.input-received",
                1,
                Instant.now(),
                Map.of("requestId", "req-audit-2"),
                "payload"
        );

        aiAuditService.recordError(context, "TIMEOUT", "Provider timeout after 5s");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT decision_id, event_id, provider, confidence, error_type, error_message
                FROM ai_decision_audit
                WHERE event_id = ?
                """,
                "evt-audit-2"
        );

        assertThat(row.get("decision_id")).isEqualTo("req-audit-2");
        assertThat(row.get("provider")).isNull();
        assertThat(row.get("confidence")).isNull();
        assertThat(row.get("error_type")).isEqualTo("TIMEOUT");
        assertThat(row.get("error_message")).isEqualTo("Provider timeout after 5s");
    }
}
