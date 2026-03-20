-- Phase 14.3: Embedding cache to avoid repeated embedding generation
CREATE TABLE embedding_cache (
    id                  BIGSERIAL PRIMARY KEY,
    content_hash        VARCHAR(64) NOT NULL,
    embedding_vector    float8[] NOT NULL,
    embedding_model     VARCHAR(100) NOT NULL,
    dimension           INTEGER NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_accessed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    access_count        INTEGER DEFAULT 1,
    UNIQUE(content_hash, embedding_model)
);
