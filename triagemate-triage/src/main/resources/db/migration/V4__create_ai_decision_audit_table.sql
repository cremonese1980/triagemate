CREATE TABLE ai_decision_audit (
    id                      BIGSERIAL PRIMARY KEY,
    decision_id             VARCHAR(255) NOT NULL,
    event_id                VARCHAR(255) NOT NULL,
    provider                VARCHAR(50),
    model                   VARCHAR(100),
    model_version           VARCHAR(100),
    prompt_version          VARCHAR(50),
    prompt_hash             VARCHAR(64),
    confidence              DOUBLE PRECISION,
    suggested_classification VARCHAR(100),
    recommends_override     BOOLEAN,
    reasoning               TEXT,
    accepted_by_validator   BOOLEAN,
    rejection_reason        VARCHAR(500),
    input_tokens            INTEGER,
    output_tokens           INTEGER,
    cost_usd                DOUBLE PRECISION,
    latency_ms              BIGINT,
    error_type              VARCHAR(50),
    error_message           TEXT,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_audit_decision_id ON ai_decision_audit(decision_id);
CREATE INDEX idx_ai_audit_event_id ON ai_decision_audit(event_id);
CREATE INDEX idx_ai_audit_created_at ON ai_decision_audit(created_at);
