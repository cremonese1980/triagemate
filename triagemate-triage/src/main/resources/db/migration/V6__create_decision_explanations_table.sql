-- Phase 14.1: Curated decision explanation dataset for RAG
CREATE TABLE decision_explanations (
    id                       BIGSERIAL PRIMARY KEY,
    decision_id              VARCHAR(255) NOT NULL,
    policy_version           VARCHAR(50),
    policy_family            VARCHAR(50),
    classification           VARCHAR(100),
    outcome                  VARCHAR(50),
    decision_reason          TEXT NOT NULL,
    decision_context_summary TEXT,
    content_hash             VARCHAR(64) NOT NULL,
    quality_score            DOUBLE PRECISION DEFAULT 0.5,
    curated_by               VARCHAR(50) DEFAULT 'system',
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    archived_at              TIMESTAMPTZ
);

CREATE INDEX idx_explanations_classification ON decision_explanations(classification);
CREATE INDEX idx_explanations_quality ON decision_explanations(quality_score);
CREATE INDEX idx_explanations_created ON decision_explanations(created_at);
CREATE INDEX idx_explanations_policy ON decision_explanations(policy_family, policy_version);
CREATE UNIQUE INDEX idx_explanations_content_hash ON decision_explanations(content_hash) WHERE archived_at IS NULL;
