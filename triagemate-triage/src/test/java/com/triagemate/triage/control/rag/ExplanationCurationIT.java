package com.triagemate.triage.control.rag;

import com.triagemate.testsupport.JdbcIntegrationTestBase;
import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionOutcome;
import com.triagemate.triage.control.decision.DecisionResult;
import com.triagemate.triage.control.decision.ReasonCode;
import com.triagemate.triage.control.policy.ConstantPolicyFamilyProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Import({
        JdbcDecisionExplanationRepository.class,
        ExplanationCurationIT.TestConfig.class
})
class ExplanationCurationIT extends JdbcIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ExplanationCurationService curationService;

    @BeforeEach
    void cleanTable() {
        jdbcTemplate.update("DELETE FROM decision_explanations");
    }

    @Test
    void curationPersistsExplanationToDatabase() {
        DecisionResult result = DecisionResult.of(
                DecisionOutcome.ACCEPT, "RULE_ERROR_KEYWORDS",
                Map.of("decisionId", "dec-it-1"),
                ReasonCode.POLICY_REJECTED, "Error keywords detected in telemetry", "1.0.0"
        );
        DecisionContext<?> context = DecisionContext.of(
                "evt-it-1", "InputReceived", 1, Instant.now(),
                Map.of("requestId", "req-it-1"), "test payload"
        );

        curationService.curateFromDecision(result, context);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM decision_explanations", Integer.class
        );
        assertThat(count).isEqualTo(1);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM decision_explanations LIMIT 1"
        );
        assertThat(row.get("decision_id")).isEqualTo("dec-it-1");
        assertThat(row.get("outcome")).isEqualTo("ACCEPT");
        assertThat(row.get("policy_family")).isEqualTo("basic-triage");
        assertThat(row.get("policy_version")).isEqualTo("1.0.0");
        assertThat(row.get("curated_by")).isEqualTo("system");
        assertThat(row.get("content_hash")).isNotNull();
    }

    @Test
    void duplicateDecisionIsSkipped() {
        DecisionResult result = DecisionResult.of(
                DecisionOutcome.REJECT, "RULE_URGENT_EMAIL",
                Map.of("decisionId", "dec-it-2"),
                ReasonCode.POLICY_REJECTED, "Urgent email escalation required", "1.0.0"
        );
        DecisionContext<?> context = DecisionContext.of(
                "evt-it-2", "InputReceived", 1, Instant.now(),
                Map.of("requestId", "req-it-2"), "test payload"
        );

        curationService.curateFromDecision(result, context);
        curationService.curateFromDecision(result, context);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM decision_explanations", Integer.class
        );
        assertThat(count).isEqualTo(1);
    }

    static class TestConfig {
        @Bean
        ExplanationCurationService explanationCurationService(
                DecisionExplanationRepository repository
        ) {
            return new ExplanationCurationService(
                    repository, new ConstantPolicyFamilyProvider("basic-triage"), 0.5
            );
        }
    }
}
