package com.triagemate.triage.control.ai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAiAuditRepository implements AiAuditRepository {

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

    public JdbcAiAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(AiAuditRecord record) {
        jdbcTemplate.update(INSERT_SQL,
                record.decisionId(),
                record.eventId(),
                record.provider(),
                record.model(),
                record.modelVersion(),
                record.promptVersion(),
                record.promptHash(),
                record.confidence(),
                record.suggestedClassification(),
                record.recommendsOverride(),
                record.reasoning(),
                record.acceptedByValidator(),
                record.rejectionReason(),
                record.inputTokens(),
                record.outputTokens(),
                record.costUsd(),
                record.latencyMs(),
                record.errorType(),
                record.errorMessage());
    }
}
