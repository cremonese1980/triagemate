# PHASE 14 — RAG over Decision Memory

## State Marker

```yaml
Change ID:    TM-14
Branch:       feat/phase-14-rag
Stage:        A (Design)
Owner:        Gabriele
Depends On:   v0.13.0 (Phase 13 — Decision Versioning & Replay)
Target Tag:   v0.14.0

Origin:       Promoted from Phase 12B.1 (RAG over Decision Memory)

Status:
  curated_dataset:          not_implemented
  embedding_pipeline:       not_implemented
  embedding_cache:          not_implemented
  vector_storage:           not_implemented
  retrieval_service:        not_implemented
  context_injection:        not_implemented
  reindexing_lifecycle:     not_implemented

Tests:
  unit:        pending
  integration: pending
  manual:      pending

Completion Criteria: NOT_MET
```

## Objective

Enrich the AI advisory layer with **historical context** through Retrieval-Augmented Generation (RAG) over curated decision explanations.

This phase transforms AI suggestions from stateless reasoning into **context-aware reasoning** grounded in real historical decisions, while preserving the deterministic-first architecture.

**This phase closes V1.0 scope.**

---

## Prerequisites

Phase 13 must be complete:
- `DecisionRecord` entity with `input_snapshot` and `attributes_snapshot`
- `PolicyVersionProvider` tracking policy versions
- `ReplayService` for decision recalculation
- Decision persistence fully operational

---

## Architectural Principles

> **RAG enriches. Policy decides. System governs.**

- **Curated, not raw:** RAG operates on curated decision explanations, not raw events — less noise, better semantic quality, cleaner governance boundaries
- **pgvector, not a separate vector DB:** Operational simplicity for V1 scale (< 100K decisions)
- **Policy-lineage aware:** Retrieval filters by policy version to prevent semantic conflicts with the deterministic pipeline
- **Graceful degradation:** RAG failure does not block advisory — AI proceeds without context
- **Bounded:** Token budget for context injection is capped (max 1500 tokens)

### Policy Family Concept

The current codebase provides `PolicyVersionProvider.currentVersion()` (e.g., `"1.0.0"`).
Phase 14 introduces the concept of **policy family** (e.g., `"basic-triage"`) via a new
`PolicyFamilyProvider` interface and `ConstantPolicyFamilyProvider` (reads from
`triagemate.policy.family`, default `"basic-triage"`). Policy family groups related policy
versions and is used for retrieval filtering in 14.5.

---

## Sub-Phases

### 14.1 — Curated Decision Explanation Dataset

**Deliverable:** Structured dataset of high-quality decision explanations for embedding

**Flyway migration:**
```sql
CREATE TABLE decision_explanations (
    id                      BIGSERIAL PRIMARY KEY,
    decision_id             VARCHAR(255) NOT NULL,
    policy_version          VARCHAR(50),
    policy_family           VARCHAR(50),
    classification          VARCHAR(100),
    outcome                 VARCHAR(50),
    decision_reason         TEXT NOT NULL,
    decision_context_summary TEXT,
    content_hash            VARCHAR(64) NOT NULL,
    quality_score           DOUBLE PRECISION DEFAULT 0.5,
    curated_by              VARCHAR(50) DEFAULT 'system',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    archived_at             TIMESTAMPTZ
);

CREATE INDEX idx_explanations_classification ON decision_explanations(classification);
CREATE INDEX idx_explanations_quality ON decision_explanations(quality_score);
CREATE INDEX idx_explanations_created ON decision_explanations(created_at);
CREATE INDEX idx_explanations_policy ON decision_explanations(policy_family, policy_version);
CREATE UNIQUE INDEX idx_explanations_content_hash ON decision_explanations(content_hash) WHERE archived_at IS NULL;
```

**Content hash:** SHA-256 hex of `classification + "|" + decision_reason`, used for duplicate detection.
Only non-archived rows enforce uniqueness (partial unique index).

**Classification mapping:** In the current codebase, `classification` maps to the `reasonCode`
from deterministic policies (e.g., `RULE_ERROR_KEYWORDS`, `RULE_URGENT_EMAIL`) or to
`suggestedClassification` from AI when an override is applied.

**Curation criteria (automated, simple for V1):**
- Decision reached final state (ACCEPT or REJECT)
- Has a non-generic `decision_reason`
- Not a duplicate (same `content_hash` among non-archived rows)

**Acceptance:**
- Retrieval source is curated, not raw events
- Explanation records are version-aware
- Curation runs as a post-decision hook (not a separate batch for V1)

---

### 14.2 — Embedding Pipeline

**Deliverable:** Generate embeddings for curated explanation records

```java
public interface EmbeddingService {
    float[] generateEmbedding(String text);
    String getModelName();
}
```

**Provider:** `SpringAiEmbeddingService` — delegates to Spring AI's `EmbeddingModel`

**Embedding provider note:** Anthropic does not offer embedding models. Embeddings use
Ollama (e.g., `nomic-embed-text`, 768 dimensions) or another Spring AI-compatible provider.
The embedding dimension (1536 in later sub-phases) should be configurable, not hardcoded,
to accommodate different models.

**Embedding input:** concatenation of:
- Normalized decision reason
- Classification
- Context summary (max 500 chars)

**Text preparation:** `EmbeddingTextPreparer` normalizes the input text before embedding:
- Lowercase, trim, collapse whitespace
- Structured format: `"classification: {X}\nreason: {Y}\ncontext: {Z}"`
- Context summary truncated to 500 characters

**Acceptance:**
- Embeddings generated from curated text
- Embedding generation is repeatable for same input
- Embedding model name tracked
- Text normalization ensures consistent embeddings for equivalent content

---

### 14.3 — Embedding Cache

**Deliverable:** Avoid repeated embedding generation

**Flyway migration:**
```sql
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
```

**Implementation notes:**
- Uses `float8[]` (native PostgreSQL array) instead of `vector(1536)` — the cache only
  needs exact-match lookups by `(content_hash, embedding_model)`, not similarity search.
  pgvector is introduced in 14.4 where similarity search is needed.
- `CachedEmbeddingService` decorates `EmbeddingService` (decorator pattern):
  on cache miss delegates to `SpringAiEmbeddingService`, persists result; on cache hit
  reuses the stored vector and updates access metadata.
- `ContentHasher` utility (SHA-256) is shared with `ExplanationCurationService` for dedup.

**Flow:**
```
content --> normalize --> SHA-256 hash --> cache lookup
                                          |-- hit  --> reuse vector, update last_accessed_at
                                          +-- miss --> generate via EmbeddingService, persist
```

**Acceptance:**
- Repeated content does not regenerate embeddings
- Embedding cost is bounded
- Cache keyed by content_hash + model name

---

### 14.4 — Vector Storage (pgvector)

**Deliverable:** Store embeddings in PostgreSQL with pgvector

**Rationale for pgvector (not a separate vector DB):**
- Operational simplicity — one database, not two
- Consistent with existing persistence posture
- Sufficient for V1 scale (< 100K decisions)

**Flyway migration:**
```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE decision_embeddings (
    id                          BIGSERIAL PRIMARY KEY,
    decision_explanation_id     BIGINT NOT NULL REFERENCES decision_explanations(id),
    embedding                   vector(1536),
    embedding_model             VARCHAR(100) NOT NULL,
    created_at                  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_embeddings_cosine
    ON decision_embeddings
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
```

**Acceptance:**
- Vector storage integrated with existing PostgreSQL
- Similarity search latency < 100ms p95 (for < 100K vectors)

---

### 14.5 — Retrieval Service

**Deliverable:** Find semantically similar historical decisions

```java
public interface DecisionMemoryService {
    List<DecisionExplanationContext> findSimilarDecisions(
        String query,
        int topK,
        RetrievalFilters filters
    );
}

public record RetrievalFilters(
    List<String> classifications,
    Double minQualityScore,
    String policyFamily,
    String minPolicyVersion
) {}
```

**Defaults:**
- `topK`: 3
- `minQualityScore`: 0.5
- `policyFamily`: current active policy family (auto-resolved)
- `minPolicyVersion`: earliest compatible policy version (configurable)

**Policy-lineage filtering:**

Decisions in the explanation dataset were generated under different policy versions.
Retrieving decisions produced under obsolete policies risks incoherent AI suggestions
that conflict with the current deterministic pipeline.

The retrieval query MUST filter by policy lineage:

```sql
WHERE policy_version >= :minPolicyVersion
  AND classification IN (:classifications)
  AND quality_score >= :minQualityScore
  AND embedding_model = :currentModel
ORDER BY embedding <=> :queryVector
LIMIT :topK
```

**Acceptance:**
- Retrieval logic isolated from advisor logic
- Top-k configurable
- Filtering by classification supported
- Retrieval filters by policy version — decisions from obsolete policies excluded
- Policy version filter is configurable with a safe default (current family only)

---

### 14.6 — Context Injection into AI Prompts

**Deliverable:** Enrich AI prompts with historical context

Before AI advisory invocation:
1. Retrieve similar historical decisions
2. Format as bounded context block
3. Inject into prompt template

**Constraints:**
- top-k: 3 (max: 5)
- Context token budget: max 1500 tokens
- No raw unbounded history dump

**Enhanced prompt structure:**
```
[...existing prompt from Phase 12A...]

SIMILAR HISTORICAL DECISIONS:
1. [Classification: DEVICE_ERROR] Reasoning: Telemetry spike detected
   Outcome: ACCEPT, resolved in 2 hours

2. [Classification: DEVICE_ERROR] Reasoning: Similar sensor pattern
   Outcome: ACCEPT, resolved in 4 hours

Consider these historical patterns in your analysis.
```

**Acceptance:**
- Prompts include curated historical context when available
- Retrieval failure does not block advisory (graceful degradation)
- Token growth remains bounded

---

### 14.7 — Embedding Re-indexing Lifecycle

**Deliverable:** Handle embedding model changes safely

When the embedding model changes (version upgrade, provider switch), all cached
embeddings become incompatible — vectors from different models are not comparable
in the same vector space.

**Rules:**
- `embedding_model` column in both `embedding_cache` and `decision_embeddings` tracks provenance
- Similarity search MUST compare vectors from the same model only
- On model change: background re-embedding job regenerates all vectors
- During re-indexing: retrieval degrades gracefully (fewer results, not wrong results)
- Old embeddings are retained until re-indexing completes, then archived

**Acceptance:**
- Embedding model change triggers re-indexing
- Retrieval never mixes vectors from different models
- System remains functional during re-indexing (graceful degradation)

---

## Verification Scenarios

### 14.V1 — RAG Retrieval
1. Curate 50 decision explanations
2. Generate embeddings
3. Query: "device telemetry anomaly"
4. Retrieve top-3 similar decisions
5. Verify relevance (same classification family)

### 14.V2 — Policy-Filtered Retrieval
1. Insert decisions from policy v1 and policy v3
2. Set `minPolicyVersion: v3`
3. Query similar decisions
4. Verify only v3 decisions are returned
5. Verify v1 decisions are excluded even if semantically closer

### 14.V3 — Embedding Cache
1. Generate embedding for text X
2. Query cache for same text → cache hit
3. Query cache for slightly different text → cache miss
4. Verify no duplicate API calls for cached content

### 14.V4 — Context Injection
1. Process event with RAG enabled
2. Verify prompt includes historical context block
3. Verify AI response references historical patterns
4. Verify token budget not exceeded

### 14.V5 — Graceful Degradation
1. Disable/break embedding service
2. Process event
3. Verify AI advisory still works (without context)
4. Verify no errors propagated to caller

### 14.V6 — Embedding Re-indexing
1. Change embedding model configuration
2. Trigger re-indexing
3. Verify old embeddings are not mixed with new
4. Verify retrieval works during and after re-indexing

---

## Done Criteria

Phase 14 is **DONE** when:

- [ ] Curated decision explanation dataset populated from decisions
- [ ] Embedding pipeline generates vectors from explanations
- [ ] Embedding cache prevents duplicate embedding generation
- [ ] pgvector stores and indexes embeddings
- [ ] Retrieval service finds similar historical decisions
- [ ] Retrieval filters by policy lineage — obsolete policy decisions excluded
- [ ] Embedding model change triggers re-indexing (no cross-model vector mixing)
- [ ] Context injection enriches AI prompts with historical context
- [ ] RAG failure degrades gracefully (advisory without context)
- [ ] All 6 verification scenarios pass
- [ ] All Phase 12A and Phase 13 tests remain green

---

## Version Target

**Release Tag:** `v0.14.0`

**This release marks V1.0 completion.**
