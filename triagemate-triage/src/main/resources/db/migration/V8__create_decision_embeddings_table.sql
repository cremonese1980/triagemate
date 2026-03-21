-- Phase 14.4: Vector storage for decision embeddings (pgvector)
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE decision_embeddings (
    id                          BIGSERIAL PRIMARY KEY,
    decision_explanation_id     BIGINT NOT NULL REFERENCES decision_explanations(id),
    embedding                   vector(768) NOT NULL,
    embedding_model             VARCHAR(100) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_embeddings_explanation ON decision_embeddings(decision_explanation_id);
CREATE INDEX idx_embeddings_model ON decision_embeddings(embedding_model);
CREATE INDEX idx_embeddings_cosine ON decision_embeddings
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 10);
