package com.triagemate.triage.control.ai;

import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AiAuditService {

    private static final Logger log = LoggerFactory.getLogger(AiAuditService.class);

    private static final String INSERT_SQL = """
            INSERT INTO ai_decision_audit (
                decision_id, event_id, provider, model, model_version,
                prompt_version, prompt_hash, confidence, suggested_classification,
                recommends_override, reasoning, accepted_by_validator, rejection_reason,
                input_tokens, output_tokens, cost_usd, latency_ms,
                error_type, error_message
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public AiAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(
            DecisionContext<?> context,
            DecisionResult deterministicResult,
            AiDecisionAdvice advice,
            ValidatedAdvice validated
    ) {
        try {
            jdbcTemplate.update(INSERT_SQL,
                    context.eventId(),                              // decision_id
                    context.eventId(),                              // event_id
                    advice.provider(),
                    advice.model(),
                    advice.modelVersion(),
                    advice.promptVersion(),
                    advice.promptHash(),
                    advice.confidence(),
                    advice.suggestedClassification(),
                    advice.recommendsOverride(),
                    advice.reasoning(),
                    validated.isAccepted(),
                    validated.rejectionReason(),
                    advice.inputTokens(),
                    advice.outputTokens(),
                    advice.costUsd(),
                    advice.latencyMs(),
                    null,                                           // error_type
                    null                                            // error_message
            );
        } catch (Exception e) {
            log.error("Failed to persist AI audit record for event {}: {}",
                    context.eventId(), e.getMessage());
        }
    }

    public void recordError(DecisionContext<?> context, String errorType, String errorMessage) {
        try {
            jdbcTemplate.update(INSERT_SQL,
                    context.eventId(),
                    context.eventId(),
                    null, null, null, null, null,
                    null, null, null, null, null, null,
                    null, null, null, null,
                    errorType,
                    errorMessage
            );
        } catch (Exception e) {
            log.error("Failed to persist AI error audit for event {}: {}",
                    context.eventId(), e.getMessage());
        }
    }
}
