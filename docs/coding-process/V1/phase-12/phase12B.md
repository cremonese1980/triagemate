# PHASE 12B — RAG + Multi-Provider

## State Marker

```yaml
Change ID:    TM-12B
Branch:       feat/phase-12b-rag-multi-provider
Stage:        A (Design)
Owner:        Gabriele
Depends On:   v0.12.0 (Phase 12A)
Target Tag:   v0.12.1

Status:
  rag_dataset:                not_implemented
  embedding_pipeline:         not_implemented
  embedding_cache:            not_implemented
  vector_storage:             not_implemented
  retrieval_service:          not_implemented
  context_injection:          not_implemented
  ollama_integration:         not_implemented
  hybrid_routing:             not_implemented
  pii_scrubbing_advanced:     not_implemented

Tests:
  unit:        pending
  integration: pending
  manual:      pending

Completion Criteria: NOT_MET
```

## Objective

Extend the AI advisory layer from Phase 12A with:
1. **RAG over curated Decision Memory** — historical context enriches AI reasoning
2. **Multi-provider support** — Ollama (local), OpenAI, Anthropic
3. **Hybrid routing** — route AI calls based on privacy level and config

Phase 12B builds on the working AI advisory pipeline from 12A. It does NOT modify the core pipeline — it enriches the prompts and adds provider flexibility.

---

## Prerequisites

Phase 12A must be complete:
- `AiDecisionAdvisor` interface working with one provider
- `AiAdvisedDecisionService` decorator operational
- Circuit breaker, fallback, audit trail all functional

---

## Sub-Phases

## 12B.1 — RAG over Decision Memory

### Goal
Use historical decision knowledge to improve AI reasoning and explanations.

RAG operates on a **curated decision explanation dataset**, not on raw events.

**Rationale:**
- less noisy retrieval
- better semantic quality
- better explainability
- lower embedding waste
- cleaner governance boundaries

#### 12B.1.a — Curated Decision Explanation Dataset

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
    quality_score           DOUBLE PRECISION DEFAULT 0.5,
    curated_by              VARCHAR(50) DEFAULT 'system',
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    archived_at             TIMESTAMP
);

CREATE INDEX idx_explanations_classification ON decision_explanations(classification);
CREATE INDEX idx_explanations_quality ON decision_explanations(quality_score);
CREATE INDEX idx_explanations_created ON decision_explanations(created_at);
CREATE INDEX idx_explanations_policy ON decision_explanations(policy_family, policy_version);
```

**Curation criteria (automated, simple for V1):**
- Decision reached final state (ACCEPT or REJECT)
- Has a non-generic `decision_reason`
- Not a duplicate (same classification + context hash)

**Acceptance:**
- retrieval source is curated, not raw events
- explanation records are version-aware
- curation runs as a post-decision hook (not a separate batch for V1)

#### 12B.1.b — Embedding Pipeline

Generate embeddings for curated explanation records.

```java
public interface EmbeddingService {
    float[] generateEmbedding(String text);
    String getModelName();
}
```

**Provider implementations:**
- `SpringAiEmbeddingService` — delegates to Spring AI's `EmbeddingModel`

**Embedding input:** concatenation of:
- normalized decision reason
- classification
- context summary (max 500 chars)

**Acceptance:**
- embeddings generated from curated text
- embedding generation is repeatable for same input
- embedding model name tracked

#### 12B.1.c — Embedding Cache

Avoid repeated embedding generation.

**Flyway migration:**
```sql
CREATE TABLE embedding_cache (
    id                  BIGSERIAL PRIMARY KEY,
    content_hash        VARCHAR(64) NOT NULL,
    embedding_vector    vector(1536),
    embedding_model     VARCHAR(100) NOT NULL,
    dimension           INTEGER NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    last_accessed_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    access_count        INTEGER DEFAULT 1,
    UNIQUE(content_hash, embedding_model)
);
```

**Flow:**
```
content --> normalize --> SHA-256 hash --> cache lookup
                                          |-- hit  --> reuse vector, update last_accessed_at
                                          +-- miss --> generate via EmbeddingService, persist
```

**Acceptance:**
- repeated content does not regenerate embeddings
- embedding cost is bounded
- cache keyed by content_hash + model name

#### 12B.1.d — Vector Storage (pgvector)

Store embeddings in PostgreSQL with pgvector.

**Rationale for pgvector (not a separate vector DB):**
- operational simplicity — one database, not two
- consistent with existing persistence posture
- sufficient for V1 scale (< 100K decisions)

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
- vector storage integrated with existing PostgreSQL
- similarity search latency < 100ms p95 (for < 100K vectors)

#### 12B.1.e — Retrieval Service

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
Policies evolve — classifications get added, thresholds change, logic mutates.
Retrieving decisions produced under obsolete policies risks incoherent AI suggestions
that conflict with the current deterministic pipeline.

The retrieval query MUST filter by policy lineage:

```sql
WHERE policy_version >= :minPolicyVersion
  AND classification IN (:classifications)
  AND quality_score >= :minQualityScore
ORDER BY embedding <=> :queryVector
LIMIT :topK
```

If `policyFamily` is set, additionally filter:
```sql
AND policy_version LIKE :policyFamily || '.%'
```

This ensures the AI only sees decisions consistent with current policy logic,
avoiding semantic conflicts between AI suggestion and deterministic output.

**Acceptance:**
- retrieval logic isolated from advisor logic
- top-k configurable
- filtering by classification supported
- retrieval filters by policy version — decisions from obsolete policies are excluded
- policy version filter is configurable with a safe default (current family only)

#### 12B.1.f — Context Injection into AI Prompts

Before AI advisory invocation:
1. Retrieve similar historical decisions
2. Format as bounded context block
3. Inject into prompt template

**Constraints:**
- top-k: 3 (max: 5)
- context token budget: max 1500 tokens
- no raw unbounded history dump

**Enhanced prompt structure:**
```
[...existing prompt from 12A...]

SIMILAR HISTORICAL DECISIONS:
1. [Classification: DEVICE_ERROR] Reasoning: Telemetry spike detected
   Outcome: ACCEPT, resolved in 2 hours

2. [Classification: DEVICE_ERROR] Reasoning: Similar sensor pattern
   Outcome: ACCEPT, resolved in 4 hours

Consider these historical patterns in your analysis.
```

**Acceptance:**
- prompts include curated historical context when available
- retrieval failure does not block advisory (graceful degradation)
- token growth remains bounded

#### 12B.1.g — Embedding Re-indexing Lifecycle

When the embedding model changes (version upgrade, provider switch), all cached
embeddings become incompatible — vectors from different models are not comparable
in the same vector space.

**Rules:**
- `embedding_model` column in both `embedding_cache` and `decision_embeddings` tracks provenance
- Similarity search MUST compare vectors from the same model only:
  ```sql
  WHERE embedding_model = :currentModel
  ```
- On model change: background re-embedding job regenerates all vectors
- During re-indexing: retrieval degrades gracefully (fewer results, not wrong results)
- Old embeddings are retained until re-indexing completes, then archived

**Acceptance:**
- embedding model change triggers re-indexing
- retrieval never mixes vectors from different models
- system remains functional during re-indexing (graceful degradation)

---

## 12B.2 — Multi-Provider Support

### Goal
Support multiple AI providers with configuration-driven selection.

#### 12B.2.a — Ollama Integration (Local Models)

```yaml
spring:
  ai:
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      chat:
        options:
          model: llama3:8b
```

**Acceptance:**
- local endpoint configurable
- Ollama provider implements same `AiDecisionAdvisor` interface
- failures fall back to deterministic

#### 12B.2.b — Provider Mode Selection

```yaml
triagemate:
  ai:
    mode: cloud  # local | cloud | hybrid
```

**Meaning:**
- `local` — Ollama only
- `cloud` — cloud provider only (from 12A)
- `hybrid` — route per event based on policy

**Acceptance:**
- mode switch is configuration-only
- no code changes required to swap runtime

#### 12B.2.c — Hybrid Provider Routing

In hybrid mode, provider selection depends on privacy constraints.

```yaml
triagemate:
  ai:
    hybrid-routing:
      default-provider: cloud
      rules:
        - privacy-level: HIGH
          provider: local
        - privacy-level: MEDIUM
          provider: cloud
```

**Acceptance:**
- routing policy explicit and configurable
- provider selection logged in audit trail
- privacy level drives routing

#### 12B.2.d — Advanced PII Handling

Extend the basic sanitizer from Phase 12A:
- Classify input by privacy level (LOW, MEDIUM, HIGH)
- Route to appropriate provider based on level
- Log privacy classification in audit trail

**Acceptance:**
- PII classification per event
- privacy level affects provider routing
- audit trail records privacy classification

---

## Verification

### 12B.V1 — RAG Retrieval

1. Curate 50 decision explanations
2. Generate embeddings
3. Query: "device telemetry anomaly"
4. Retrieve top-3 similar decisions
5. Verify relevance (same classification family)

### 12B.V1b — Policy-Filtered Retrieval

1. Insert decisions from policy v1 and policy v3
2. Set `minPolicyVersion: v3`
3. Query similar decisions
4. Verify only v3 decisions are returned
5. Verify v1 decisions are excluded even if semantically closer

### 12B.V2 — Embedding Cache

1. Generate embedding for text X
2. Query cache for same text → cache hit
3. Query cache for slightly different text → cache miss
4. Verify no duplicate API calls for cached content

### 12B.V3 — Context Injection

1. Process event with RAG enabled
2. Verify prompt includes historical context block
3. Verify AI response references historical patterns
4. Verify token budget not exceeded

### 12B.V4 — Local Model (Ollama)

1. Configure `mode: local`
2. Process event
3. Verify AI call goes to Ollama (not cloud)
4. Verify same audit trail format

### 12B.V5 — Hybrid Routing (Privacy)

1. Configure `mode: hybrid`
2. Process HIGH privacy event → routed to local
3. Process LOW privacy event → routed to cloud
4. Verify audit trail records provider selection

---

## Done Criteria

Phase 12B is complete when:

- [ ] Curated decision explanation dataset populated from decisions
- [ ] Embedding pipeline generates vectors from explanations
- [ ] Embedding cache prevents duplicate embedding generation
- [ ] pgvector stores and indexes embeddings
- [ ] Retrieval service finds similar historical decisions
- [ ] Retrieval filters by policy lineage — obsolete policy decisions excluded
- [ ] Embedding model change triggers re-indexing (no cross-model vector mixing)
- [ ] Context injection enriches AI prompts with historical context
- [ ] Ollama integration works for local model execution
- [ ] Hybrid routing directs calls based on privacy level
- [ ] RAG failure degrades gracefully (advisory without context)
- [ ] All 6 verification scenarios pass
- [ ] All Phase 12A tests remain green

---

## Version Target

**Release Tag:** `v0.12.1`
