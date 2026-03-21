# Phase 14 — Deep Review: RAG (Retrieval-Augmented Generation)

## 1. Architectural Assessment

### 1.1 Strengths

**Optional Feature Toggle (Excellent)**
The entire RAG subsystem is behind `triagemate.rag.enabled=true` via `@ConditionalOnProperty`. When RAG is disabled, zero beans are created, zero overhead. The router uses `@Nullable` injection and null-checks — the system works identically with or without RAG.

**Deterministic-before-AI Preserved**
Curation happens *after* the deterministic decision and persistence (`DefaultDecisionRouter:43-45`). The RAG pipeline never alters decision flow — it only *records* and *retrieves*. This is architecturally sound.

**Graceful Degradation Everywhere**
Every RAG operation is wrapped in try-catch with fallback to empty/no-op:
- `DefaultDecisionRouter:57-67` — curation failure logged as warning, routing continues
- `DefaultDecisionMemoryService:30-41` — retrieval failure returns empty list
- `CachedEmbeddingService:44-49` — cache write failure still returns the embedding
- `SpringAiDecisionAdvisor:182-209` — historical context failure returns ""

**Clean Separation of Concerns**
The pipeline is decomposed into well-defined stages: Curation → Hashing → Text Preparation → Embedding → Caching → Vector Storage → Retrieval → Formatting. Each has its own class with a single responsibility.

### 1.2 Architectural Concerns

**ARCH-1: Curation and Embedding Are Decoupled but Not Linked (Medium Risk)**
`ExplanationCurationService` saves explanations to the database, but there is **no automatic trigger** to generate embeddings for newly curated explanations. Embeddings are only created via `EmbeddingReindexService.reindex()`, which must be called externally (no scheduler, no event listener, no post-save hook). This means freshly curated explanations are invisible to RAG retrieval until someone triggers reindex.

**ARCH-2: No Expiration/Eviction for Embedding Cache (Low Risk)**
`embedding_cache` table grows indefinitely. There is `access_count` and `last_accessed_at` tracking, but no eviction logic. For long-running systems, this table could grow unbounded.

**ARCH-3: IVFFLAT Index with `lists=10` is a Static Choice (Low Risk)**
The `V8` migration hardcodes `WITH (lists = 10)` for the IVFFLAT index. This is reasonable for small datasets (<10K rows), but becomes suboptimal as the dataset grows. IVFFLAT also requires `VACUUM` after bulk inserts to update the index. pgvector's HNSW index would be a better default for production.

**ARCH-4: Policy Version Comparison is Lexicographic, Not Semantic (Medium Risk)**
`JdbcDecisionEmbeddingRepository:95`: `dx.policy_version >= ?` does string comparison. Version "9.0.0" > "10.0.0" lexicographically. If versions follow semver, this filter will silently return wrong results.

---

## 2. Potential Bugs

### BUG-1: Race Condition in Deduplication (Medium Severity)
`ExplanationCurationService:61-64`:
```java
if (repository.existsByContentHash(contentHash)) { return; }
// ...
repository.save(explanation);
```
This is a classic TOCTOU (Time-of-Check-Time-of-Use) race. Two concurrent decisions with the same content hash could both pass the check, and the second `save()` would hit the unique constraint and throw. The `DefaultDecisionRouter` catches this, but the exception is logged as a warning rather than handled as expected behavior.

**Fix**: Use `INSERT ... ON CONFLICT (content_hash) WHERE archived_at IS NULL DO NOTHING` or catch `DuplicateKeyException` silently.

### BUG-2: Similarity Score Semantics Are Inverted (Medium Severity)
`JdbcDecisionEmbeddingRepository:79`: The column alias `similarity_score` is actually *cosine distance* (`<=>` operator), not similarity. Distance 0 = identical, distance 2 = opposite. But consumers (e.g., `HistoricalContextFormatter`) may interpret higher scores as "more similar". This is confusing and could lead to misuse.

The field in `DecisionExplanationContext` is named `similarityScore` but contains a distance value. This is semantically incorrect.

### BUG-3: `findAllNonArchived()` Loads Entire Table into Memory (High Severity in Scale)
`EmbeddingReindexService:31` calls `explanationRepository.findAllNonArchived()`, which loads ALL non-archived explanations into a single `List`. With thousands of explanations, this is an OOM risk. Should use pagination/streaming.

### BUG-4: `toVectorLiteral` Uses `Float.toString()` Locale Sensitivity (Low Severity)
`JdbcDecisionEmbeddingRepository:148-156`: `sb.append(vector[i])` uses `Float.toString()` which always uses '.' as decimal separator (Java spec), so this is actually safe. But the `parseVector` method at line 158 doesn't handle empty bracket case "[]" — it would produce a single-element array with a parsing error.

### BUG-5: Missing `@Transactional` on Curation Path (Medium Severity)
`ExplanationCurationService.curateFromDecision()` does a read (`existsByContentHash`) then a write (`save`). These are not in a single transaction. Combined with BUG-1, this means the dedup check and save are not atomic.

### BUG-6: `DecisionEmbedding` Record with `float[]` — Broken `equals()`/`hashCode()` (Low Severity)
Java records generate `equals()`/`hashCode()` based on field values. But `float[]` uses reference equality by default. Two `DecisionEmbedding` objects with identical embeddings are NOT equal. This is a known Java footgun with records containing arrays.

---

## 3. SOLID Principles Analysis

### Single Responsibility (SRP) ✅
Each class has one clear job:
- `ContentHasher` — hashing
- `EmbeddingTextPreparer` — text normalization
- `CachedEmbeddingService` — caching decorator
- `ExplanationCurationService` — curation logic
- `HistoricalContextFormatter` — prompt formatting

No violations detected.

### Open/Closed (OCP) ✅
- `EmbeddingService` interface allows new providers without touching existing code
- `DecisionExplanationRepository`/`DecisionEmbeddingRepository` interfaces decouple persistence

### Liskov Substitution (LSP) ✅
- `CachedEmbeddingService` correctly implements `EmbeddingService` and is a proper behavioral substitute

### Interface Segregation (ISP) ⚠️
- `DecisionEmbeddingRepository` has 8 methods. Some are only used by `EmbeddingReindexService` (`existsByExplanationIdAndModel`, `deleteByModelNot`, `countByModel`), while others only by `DefaultDecisionMemoryService` (`findSimilarWithFilters`). Consider splitting into `EmbeddingQueryRepository` and `EmbeddingManagementRepository`.

### Dependency Inversion (DIP) ✅
- Services depend on interfaces (`EmbeddingService`, `DecisionMemoryService`, repositories)
- Configuration wires implementations via `RagConfig`
- `DefaultDecisionRouter` depends on the concrete `ExplanationCurationService` — acceptable since it's `@Nullable` and optional

---

## 4. Elegance and Code Quality

### Positives
- Records are used extensively and appropriately for value objects
- Factory methods (`create()`) provide clean construction with sensible defaults
- `RagProperties` compact constructor provides defensive defaults
- `ContentHasher` is clean and minimal
- Structured logging is used consistently

### Improvement Opportunities

**STYLE-1**: `ExplanationCurationService.resolveClassification()` mixes concerns — it knows about AI override attributes (`aiOverrideApplied`, `aiSuggestedClassification`) by accessing a raw `Map<String, Object>`. This couples the curation service to AI advisory's internal attribute naming convention. A typed DTO or a classification resolver strategy would be cleaner.

**STYLE-2**: `SpringAiDecisionAdvisor` has 8 constructor parameters, 2 constructors, and creates `HistoricalContextFormatter` internally via `new`. This violates the project's otherwise clean DI pattern.

**STYLE-3**: `JdbcDecisionEmbeddingRepository.findSimilarWithFilters()` builds SQL via `StringBuilder` with dynamic WHERE clauses. This is correct but fragile — no test validates the generated SQL syntax for edge cases (empty classifications list, all filters null, etc.).

---

## 5. Performance Considerations

### PERF-1: Double Vector Computation in `findSimilarWithFilters` (Medium Impact)
The `ORDER BY de.embedding <=> ?::vector` clause uses `vectorLiteral` which is added as a *separate parameter* from the WHERE clause. pgvector parses and casts the vector literal twice. Use a CTE or subquery to materialize the vector once.

### PERF-2: N+1 Pattern in Reindex (Medium Impact)
`EmbeddingReindexService.reindex()` iterates all explanations and for each:
1. Calls `existsByExplanationIdAndModel()` — 1 query
2. Calls `embeddingService.generateEmbedding()` — 1 API call
3. Calls `embeddingRepository.save()` — 1 query

For N explanations, this is 3N operations. A batch approach (query all existing, compute missing, bulk insert) would be significantly faster.

### PERF-3: `findAllNonArchived()` Without Limit (High Impact at Scale)
As noted in BUG-3, loading the entire table is both a memory and latency risk. Pagination with batch processing would be safer.

### PERF-4: Embedding Cache Has No TTL (Low Impact)
Stale embeddings from old models persist forever. While `EmbeddingReindexService.purgeOldModelEmbeddings()` exists, it doesn't touch the embedding_cache table — only the decision_embeddings table. Old cached embeddings in `embedding_cache` are never cleaned.

### PERF-5: `ContentHasher` Creates New `MessageDigest` per Call
`MessageDigest.getInstance("SHA-256")` is called on every hash. While the JDK caches provider lookups, reusing a `ThreadLocal<MessageDigest>` would be marginally faster under high throughput.

---

## 6. Unit Test Coverage Assessment

| Class | Tests | Covered Scenarios | Missing Scenarios |
|---|---|---|---|
| `ExplanationCurationService` | 14 | Outcomes, dedup, classification resolution, generic reasons, AI overrides | TOCTOU race, null context.attributes(), concurrent calls |
| `CachedEmbeddingService` | 6 | Cache hit/miss, null/blank text, cache save failure | Concurrent access, cache poisoning |
| `DefaultDecisionMemoryService` | 6 | Success, null/blank query, empty embedding, graceful degradation | topK=0, negative topK, null filters |
| `EmbeddingReindexService` | 7+ | Reindex, skip, failure, purge, isReindexNeeded | Partial failure recovery, concurrent reindex |
| `EmbeddingTextPreparer` | 8 | Normalization, truncation, null fields | Unicode, very large input, special characters |
| `ContentHasher` | 6 | Determinism, format, null/empty | Thread safety (actually safe by design) |
| `SpringAiEmbeddingService` | 5 | Generate, model name, empty/null | Exception from underlying model |
| `RagProperties` | 3 | Defaults, custom, retrieval defaults | Boundary values (0, negative, max) |
| `HistoricalContextFormatter` | ✅ | Format, budget, truncation, empty | Budget overflow edge cases |

**Overall unit test grade: B+** — Good coverage of happy paths and basic error cases. Missing concurrency testing and boundary value analysis.

---

## 7. Integration Test Coverage Assessment

| Area | Tests | Covered | Missing |
|---|---|---|---|
| `DecisionExplanationRepository` | 6 | CRUD, dedup, unique constraint, archived duplicates | Update/archive operations, pagination |
| `EmbeddingCacheRepository` | 6 | Save/find, model distinction, access metadata, precision | Cleanup, TTL, bulk operations |
| `DecisionEmbeddingRepository` | 6 | Save/find, similarity, model filter, limit | Edge vectors (zeros), concurrent writes |
| `DecisionMemoryServiceIT` | 8+ | Filters (all types), archived exclusion, limit | Tests repo directly, not `DefaultDecisionMemoryService` |
| `EmbeddingReindexIT` | 6 | End-to-end reindex, idempotence, purge, model drift | Mixed success/failure, large datasets |
| `ExplanationCurationIT` | 2 | Persistence, duplicate skip | **Severely under-tested** — needs outcome filtering, classification, context truncation |

**Critical gap**: `ExplanationCurationIT` has only 2 tests. The end-to-end curation flow is the most business-critical part and deserves at minimum 8-10 integration tests.

**Missing end-to-end flow**: There is no integration test that exercises the complete pipeline: Decision → Curation → Embedding → Retrieval → Prompt Enrichment. Each layer is tested in isolation but the glue is untested.

**Overall integration test grade: B-** — Good repository coverage but weak service-level integration tests.

---

## 8. Manual Test Checklist

### 8.1 Feature Toggle

- [ ] **MT-01**: Start application with `triagemate.rag.enabled=false`. Verify no RAG beans are created (check logs for bean registration). Process a decision and confirm no entries in `decision_explanations` table.
- [ ] **MT-02**: Start application with `triagemate.rag.enabled=true` but no `EmbeddingModel` bean (no AI provider configured). Verify `ExplanationCurationService` is created but `CachedEmbeddingService`, `DefaultDecisionMemoryService`, and `EmbeddingReindexService` are NOT created. Decisions should be curated but no embeddings generated.
- [ ] **MT-03**: Start application with `triagemate.rag.enabled=true` and Ollama configured. Verify full bean chain is created.

### 8.2 Explanation Curation

- [ ] **MT-04**: Send a message that results in ACCEPT outcome. Verify a row is inserted in `decision_explanations` with correct `decision_id`, `classification`, `outcome="ACCEPT"`, `curated_by="system"`, `quality_score=0.5`.
- [ ] **MT-05**: Send a message that results in REJECT outcome. Verify curation works identically to ACCEPT.
- [ ] **MT-06**: Send a message that results in RETRY outcome. Verify **no** row is inserted in `decision_explanations`.
- [ ] **MT-07**: Send a message that results in DEFER outcome. Verify **no** row is inserted.
- [ ] **MT-08**: Send the same message twice (identical classification + reason). Verify only one row exists in `decision_explanations` (deduplication via content_hash).
- [ ] **MT-09**: Send a message with a generic reason ("unknown", "default", "none", "n/a", empty). Verify no curation occurs.
- [ ] **MT-10**: Send a message with a very long payload (>500 chars). Verify `decision_context_summary` is truncated to exactly 500 characters.
- [ ] **MT-11**: Verify that curation failure (e.g., database down temporarily) does NOT prevent the decision from being routed and published to Kafka.

### 8.3 Embedding Generation and Caching

- [ ] **MT-12**: With RAG enabled and Ollama running, trigger reindex (`EmbeddingReindexService.reindex()`). Verify embeddings are created in `decision_embeddings` table for each non-archived explanation.
- [ ] **MT-13**: Run reindex again. Verify it's idempotent (skips already-indexed explanations, `ReindexResult.created=0`).
- [ ] **MT-14**: Verify `embedding_cache` table has entries after reindex. Check that `content_hash`, `embedding_model`, and `dimension` are populated correctly.
- [ ] **MT-15**: Generate the same embedding text twice. Verify second call hits the cache (check logs for "Embedding cache hit" message, verify `access_count` incremented in `embedding_cache`).
- [ ] **MT-16**: Change `triagemate.rag.embedding-model` property. Call `isReindexNeeded()` and verify it returns `true`. Run reindex, verify new embeddings are created with the new model name.
- [ ] **MT-17**: After model change reindex, call `purgeOldModelEmbeddings(newModel)`. Verify old model embeddings are deleted from `decision_embeddings`.

### 8.4 Vector Similarity Retrieval

- [ ] **MT-18**: With multiple explanations indexed, query `DefaultDecisionMemoryService.findSimilarDecisions()` with a query similar to an existing explanation. Verify results are returned ordered by relevance (most similar first).
- [ ] **MT-19**: Query with a completely unrelated text. Verify results are returned but with higher distance scores (lower similarity).
- [ ] **MT-20**: Query with `topK=1`. Verify only one result is returned.
- [ ] **MT-21**: Archive an explanation (`SET archived_at = NOW()`). Query again and verify the archived explanation is excluded from results.
- [ ] **MT-22**: Query with `RetrievalFilters` specifying a specific `policyFamily`. Verify only explanations from that family are returned.
- [ ] **MT-23**: Query with `minQualityScore=0.9`. Verify only high-quality explanations are returned (default score is 0.5, so no results expected unless manually updated).

### 8.5 AI Advisory Integration (RAG → Prompt)

- [ ] **MT-24**: With RAG enabled and explanations indexed, trigger an AI advisory call. Verify the prompt sent to the AI contains the "SIMILAR HISTORICAL DECISIONS:" section with formatted historical context.
- [ ] **MT-25**: With RAG enabled but no explanations indexed. Verify the AI advisory call proceeds without historical context (empty string, no header/footer).
- [ ] **MT-26**: With RAG disabled. Verify the AI advisory call proceeds without historical context.
- [ ] **MT-27**: With RAG enabled but DecisionMemoryService unavailable (e.g., Ollama down). Verify the AI advisory call proceeds without historical context and logs a warning.

### 8.6 Database Integrity

- [ ] **MT-28**: Inspect `decision_explanations` table schema. Verify unique index on `content_hash WHERE archived_at IS NULL` exists.
- [ ] **MT-29**: Inspect `decision_embeddings` table. Verify `vector(768)` column type, FK to `decision_explanations(id)`, and IVFFLAT cosine index exist.
- [ ] **MT-30**: Inspect `embedding_cache` table. Verify `UNIQUE(content_hash, embedding_model)` constraint exists.
- [ ] **MT-31**: Try to insert a `decision_embedding` with a non-existent `decision_explanation_id`. Verify FK constraint violation.

### 8.7 Resilience and Edge Cases

- [ ] **MT-32**: Kill the Ollama/AI provider while reindex is in progress. Verify reindex completes with `failed > 0` count and doesn't crash.
- [ ] **MT-33**: Send 100+ decisions rapidly. Verify curation handles concurrent writes without exceptions (or handles `DuplicateKeyException` gracefully).
- [ ] **MT-34**: With a large number of explanations (>1000), verify reindex completes within a reasonable time and doesn't cause OOM.
- [ ] **MT-35**: Verify application startup time is not significantly impacted with RAG enabled (no blocking initialization).

### 8.8 Configuration Validation

- [ ] **MT-36**: Set `triagemate.rag.embedding-dimension=384`. Verify the system still works (dimension change is only cosmetic in properties, actual vector dimension comes from the model).
- [ ] **MT-37**: Set `triagemate.rag.retrieval.top-k=10`. Verify retrieval returns up to 10 results.
- [ ] **MT-38**: Set `triagemate.rag.retrieval.min-quality-score=0.8`. Verify retrieval filters out explanations with quality_score < 0.8.
- [ ] **MT-39**: Set `triagemate.rag.default-quality-score=0.7`. Verify newly curated explanations get quality_score=0.7.

### 8.9 Observability

- [ ] **MT-40**: Check structured logs for curation events: look for `"Decision explanation curated id=X decisionId=Y classification=Z"`.
- [ ] **MT-41**: Check structured logs for cache behavior: look for `"Embedding cache hit"` and `"Embedding cache miss"` messages.
- [ ] **MT-42**: Check structured logs for reindex: look for `"Embedding reindex complete model=X created=Y skipped=Z failed=W"`.
- [ ] **MT-43**: Check warning logs when curation fails: look for `"Explanation curation failed, continuing with routing"`.
