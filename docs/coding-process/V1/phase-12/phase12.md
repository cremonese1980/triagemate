# PHASE 12 — AI Decision Support Engine

## State Marker

```yaml
Change ID:    TM-12
Branch:       feat/phase-12-ai-decision-support
Stage:        A (Design)
Owner:        Gabriele
Depends On:   v0.11.x
Target Tag:   v0.12.0

AI Integration Status:
  classifier_abstraction:   not_implemented
  ai_advisor:               not_implemented
  rag_memory:               not_implemented
  local_model_support:      not_implemented
  safety_gates:             not_implemented
  ai_audit_trail:           not_implemented
  prompt_versioning:        not_implemented
  embedding_cache:          not_implemented
  quality_metrics:          not_implemented
  prompt_governance:        not_implemented
  model_lifecycle:          not_implemented
  dataset_curation:         not_implemented
  observability:            not_implemented

Tests:
  unit:        pending
  integration: pending
  manual:      pending

Completion Criteria: NOT_MET
```

## Objective

Introduce AI into TriageMate as **decision support, not blind automation**. Phase 12 is the core identity phase of the product: the system evolves from a deterministic decision engine into a **deterministic engine with an AI advisory layer**. The deterministic system remains authoritative; AI provides classification support, contextual reasoning, retrieval over historical decisions, and explainability enrichment.

![ms-roadmap](ms-roadmap)

---

## Design Freeze

The following architectural decisions are **fixed** for Phase 12.

### Decision 1 — AI position in the pipeline

AI is placed **after deterministic decisioning**, as an advisor.

```
Input Event
   ↓
Deterministic Classifier / Policy
   ↓
AI Decision Advisor
   ↓
Decision Validator
   ↓
Final Decision
```

**Rationale:**
- preserves deterministic replay semantics for Phase 13
- preserves policy authority
- makes AI optional and removable
- keeps auditability stronger
- allows AI to suggest overrides without becoming authoritative

**Rule:**
> AI suggests.  
> Policy validates.  
> System decides.

### Decision 2 — Classifier abstraction remains swappable

Even though AI is not authoritative in Phase 12, the classification layer must be architecturally prepared so that a future AI-first classifier can be introduced without breaking the pipeline.

This means classification must already be **abstracted behind a stable interface**.

### Decision 3 — RAG dataset is curated, not raw

RAG must operate on a **curated decision explanation dataset**, not on raw events.

**Rationale:**
- less noisy retrieval
- better semantic quality
- better explainability
- lower embedding waste
- cleaner governance boundaries

### Decision 4 — Model and prompt lifecycle are versioned and governed

Both AI models and prompt templates are versioned assets with explicit lifecycle management, approval workflows, and rollback capabilities.

**Rationale:**
- model drift detection
- prompt regression prevention
- reproducibility over time
- governance compliance

### Decision 5 — Privacy-first prompt assembly

No PII or sensitive data enters AI prompts without explicit sanitization, anonymization, or policy approval.

**Rationale:**
- GDPR compliance
- data residency requirements
- third-party provider risk mitigation

---

## Architectural Position

Phase 12 adds AI in a way that does **not break** the deterministic backbone built in Phases 7–11.

![ms-roadmap](ms-roadmap)

### Baseline flow

```
InputReceivedEvent
      ↓
Classification Engine
      ↓
Deterministic Policy Engine
      ↓
AI Decision Advisor
      ↓
Decision Validator
      ↓
DecisionMadeEvent
```

### Classification engine strategy

```
ClassificationEngine
  ├── RuleBasedClassifier
  ├── HybridClassifier
  └── AiClassifier   (future-capable, not authoritative by default)
```

This preserves future flexibility while keeping the current design safe.

---

## Non-Negotiable Principles

### 1. AI output is untrusted input
Every AI output must pass deterministic validation.

### 2. Deterministic system remains primary authority
A decision must always be possible without AI.

### 3. Every AI call must be auditable
No opaque decision path is allowed.

### 4. AI must be bounded
Every AI interaction must be bounded by:
- cost budget
- latency budget
- provider timeout
- schema validation
- fallback rules

### 5. Replay safety matters
Anything that breaks replayability or governance must not become authoritative in V1.0.

### 6. Quality is measurable
AI subsystem quality must be quantifiable through metrics, not assumptions.

### 7. Privacy is non-negotiable
PII and sensitive data handling must be explicit, auditable, and compliant.

---

## Sub-Phase Dependencies

The following dependency graph shows implementation order constraints:

```
12.1 (AI Classification Foundation)
  ↓
12.2 (AI Advisory Decision Layer)
  ↓
  ├─→ 12.3 (RAG over Decision Memory)
  ├─→ 12.4 (Local Model Support)
  ├─→ 12.5 (AI Safety & Governance)
  └─→ 12.6 (Concurrency & Runtime Sanity)
       ↓
     12.7 (AI Quality Metrics)
       ↓
     12.8 (Observability & Monitoring)
```

**Critical path:** 12.1 → 12.2 → 12.7 → 12.8

**Parallel tracks:**
- 12.3, 12.4, 12.5, 12.6 can be developed in parallel after 12.2
- 12.7 and 12.8 should be last to integrate all subsystems

---

## Sub-Phases

## 12.1 — AI Classification Foundation

### Goal
Introduce AI-ready classification architecture without making AI authoritative.

This sub-phase builds the abstraction layer that allows:
- rule-only classification now
- hybrid classification later
- AI-first classification in the future

#### 12.1.a — Classification Engine Abstraction

Create a stable classification interface.

**Example:**
```java
public interface ClassificationEngine {
    ClassificationResult classify(InputEvent event, DecisionContext context);
}
```

**Core implementations:**
- `RuleBasedClassificationEngine`
- `HybridClassificationEngine`
- `AiClassificationEngine` (future-ready)

**Acceptance:**
- business flow depends on interface, not implementation
- current implementation remains deterministic
- future AI-first classifier can be swapped in without pipeline rewrite

#### 12.1.b — AI Adapter Layer

Introduce a provider-neutral AI adapter.

**Example:**
```java
public interface AiDecisionAdvisor {
    AiDecisionAdvice advise(InputEvent event, DecisionContext context, DeterministicDecision decision);
}
```

**Provider implementations:**
- `OpenAiDecisionAdvisor`
- `ClaudeDecisionAdvisor`
- `OllamaDecisionAdvisor`

**Responsibilities:**
- prompt building
- provider invocation
- response parsing
- latency tracking
- cost tracking
- schema validation

**Acceptance:**
- core business code has no provider-specific coupling
- AI providers can be swapped via configuration
- adapter boundary is explicit and testable

#### 12.1.c — Spring AI Integration

Integrate Spring AI as the provider orchestration layer for cloud models.

![ms-roadmap](ms-roadmap)

**Expected integrations:**
- OpenAI
- Anthropic
- common prompt abstraction

**Configuration example:**
```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o-mini
      model-version: "2024-11-20"
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      model: claude-sonnet-4-20250514
      model-version: "20250514"
```

**Acceptance:**
- provider config externalized
- no secret in code
- environment-specific enablement supported
- **model version explicitly tracked**

#### 12.1.d — Prompt Template Discipline

Create versioned, deterministic prompt templates with governance workflow.

**Required characteristics:**
- narrow purpose
- structured fields only
- no free-form hidden logic
- JSON-only response instructions
- immutable versioning
- approval workflow

**Example prompt intent:**
```
Classify the event into one of the allowed categories.
Return JSON only.
Do not invent categories.
```

**Prompt versioning structure:**
```
prompts/
  classification/
    v1.0.0/
      device-error-classifier.txt
      metadata.yaml
    v1.1.0/
      device-error-classifier.txt
      metadata.yaml
      CHANGELOG.md
```

**Metadata fields:**
```yaml
prompt_id: "device-error-classifier"
version: "1.1.0"
approved_by: "gabriele@triagemate.io"
approved_at: "2025-03-10T10:00:00Z"
replaces: "1.0.0"
compatible_models:
  - gpt-4o-mini
  - claude-sonnet-4
testing_results:
  precision: 0.92
  recall: 0.89
  f1_score: 0.90
```

**Governance workflow:**
1. Draft new prompt version
2. A/B test against current version
3. Quality metrics validation (see 12.7)
4. Approval by designated owner
5. Deployment with rollback plan
6. Monitor for regression (7-day observation window)

**Rollback triggers:**
- Quality metrics degradation >5%
- Validator rejection rate increase >10%
- Cost increase >20%
- Latency increase >50%

**Acceptance:**
- prompts are stored as versioned assets
- prompt template lookup is explicit
- prompt content is hashable and auditable
- **approval workflow enforced**
- **A/B testing capability exists**
- **rollback is tested and documented**

#### 12.1.e — Structured Output Contract

All AI classification responses must follow a strict schema.

**Example:**
```json
{
  "classification": "DEVICE_ERROR",
  "confidence": 0.83,
  "reasoning": "Detected telemetry anomaly pattern"
}
```

**Validation rules:**
- mandatory fields present
- allowed enum values enforced
- confidence range enforced [0.0, 1.0]
- malformed JSON rejected

**Acceptance:**
- no free-form response parsing
- schema failures trigger fallback
- response parsing is covered by tests

#### 12.1.f — Deterministic Fallback

If AI fails, the system falls back to deterministic classification.

**AI failure conditions include:**
- timeout
- unavailable provider
- malformed output
- low confidence
- cost threshold exceeded
- validation rejection

**Error taxonomy:**

**Transient errors** (retry eligible):
- network timeout
- provider rate limit (429)
- provider temporary unavailability (503)

**Permanent errors** (no retry):
- invalid API key (401)
- malformed response schema
- confidence below threshold
- validation rejection

**Retry logic:**
```java
RetryConfig retryConfig = RetryConfig.custom()
    .maxAttempts(3)
    .waitDuration(Duration.ofMillis(500))
    .retryOnException(e -> e instanceof TransientAiException)
    .build();
```

**Error budget:**
- Max AI errors per hour: 100
- Max consecutive AI failures: 5
- When error budget exceeded: switch to deterministic-only mode for 10 minutes

**Acceptance:**
- classification always completes
- no AI dependency for correctness
- fallback path is explicit and tested
- **error classification is explicit**
- **retry logic is differentiated**
- **error budget enforced**

#### 12.1.g — Model Lifecycle Management

Introduce explicit model version tracking and migration strategy.

**Model registry table:** `ai_model_registry`

**Fields:**
- `model_id` (e.g., "gpt-4o-mini")
- `model_version` (e.g., "2024-11-20")
- `provider` (e.g., "openai")
- `status` (active, deprecated, sunset)
- `sunset_date`
- `migration_target` (model to migrate to)
- `baseline_metrics` (JSON: precision, recall, latency, cost)
- `registered_at`
- `last_verified_at`

**Lifecycle states:**
```
active → deprecated → sunset
```

**Migration workflow:**
1. Provider announces model deprecation
2. System marks model as `deprecated`, sets `sunset_date`
3. Parallel testing with `migration_target` model
4. Quality metrics comparison (see 12.7)
5. Gradual traffic shift (canary deployment)
6. Full cutover before `sunset_date`
7. Old model marked as `sunset`

**Model drift detection:**
- Weekly quality baseline comparison
- Alert if metrics degrade >5% from baseline
- Automatic re-calibration trigger

**Acceptance:**
- model version explicitly tracked in audit trail
- model deprecation notifications handled
- migration tested without downtime
- drift detection automated

---

## 12.2 — AI Advisory Decision Layer

### Goal
Allow AI to enrich or challenge deterministic decisions without becoming authoritative.

This is the core Phase 12 design choice.

#### 12.2.a — Deterministic Decision First

The deterministic decision is produced first.

**Example outputs:**
- classification
- route
- priority
- explanation seed

**Acceptance:**
- deterministic decision exists before any AI call
- AI receives deterministic output as context
- deterministic engine remains replay-safe

#### 12.2.b — AI Advice Model

Introduce a structured advisory object.

**Example:**
```java
public record AiDecisionAdvice(
        String suggestedClassification,
        String suggestedRoute,
        double confidence,
        String reasoning,
        boolean recommendsOverride,
        Map<String, Double> alternativeClassifications  // for calibration analysis
) {}
```

**Acceptance:**
- AI advice is separate from final decision
- advice is explicit, typed, and serializable
- no implicit override behavior
- **includes alternative classifications for confidence calibration**

#### 12.2.c — Decision Validator

Introduce deterministic validation of AI advice.

**Validator responsibilities:**
- check allowed override scope
- reject unsupported classifications
- reject low-confidence suggestions
- reject invalid routing targets
- ensure policy constraints are respected
- **calibrated confidence threshold enforcement**

**Confidence calibration:**

Initial static thresholds:
- `min_confidence_for_override`: 0.85
- `min_confidence_for_suggestion`: 0.70

Dynamic calibration (see 12.7.c):
- Track actual accuracy vs. predicted confidence
- Adjust thresholds based on empirical performance
- Re-calibrate weekly

**Possible outcomes:**
- accept advice (confidence >= 0.85, valid classification)
- partially accept advice (confidence >= 0.70, advisory only)
- reject advice entirely (confidence < 0.70 or invalid)

**Acceptance:**
- validator is deterministic
- AI cannot bypass policy
- final decision remains governed
- **confidence thresholds are calibrated, not arbitrary**

#### 12.2.d — Final Decision Assembly

Combine:
- deterministic decision
- optional AI advice
- validator result

into one final `DecisionResult`.

Persist whether AI influenced the outcome.

**Example fields:**
- `ai_advice_present`
- `ai_advice_accepted`
- `ai_advice_rejected_reason`
- `ai_confidence`
- `ai_model_version`

**Acceptance:**
- final decision lineage is explicit
- audit clearly shows AI influence or rejection
- no hidden mutation of decision result

---

## 12.3 — RAG over Decision Memory

### Goal
Use historical decision knowledge to improve reasoning and explanations. The roadmap explicitly requires RAG over decision memory.

![ms-roadmap](ms-roadmap)

#### 12.3.a — Curated Decision Explanation Dataset

Create a dedicated dataset for semantic retrieval.

This is **not** raw event storage.

**Table concept:** `decision_explanations`

**Fields:**
- `decision_id`
- `policy_version`
- `classification`
- `route`
- `outcome`
- `decision_reason`
- `decision_context_summary`
- `quality_score` (0.0-1.0, for curation filtering)
- `curated_by` (system or human reviewer)
- `created_at`
- `archived_at`

**Acceptance:**
- retrieval source is curated
- no raw noisy payload dependence
- explanation records are version-aware
- **quality scoring enables filtering**

#### 12.3.b — Embedding Pipeline

Generate embeddings for curated explanation records.

**Embedding input should include:**
- normalized decision reason
- summarized context
- policy version tags
- relevant classification metadata

**Embedding generation:**
```java
public interface EmbeddingService {
    float[] generateEmbedding(String text, String modelName);
}
```

**Supported embedding models:**
- `text-embedding-3-small` (OpenAI)
- `voyage-2` (Voyage AI)
- `local/all-MiniLM-L6-v2` (Ollama local)

**Acceptance:**
- embeddings generated from curated text
- embedding generation is repeatable
- embeddings are decoupled from provider choice
- **embedding model explicitly tracked**

#### 12.3.c — Embedding Cache

Introduce caching to avoid repeated embedding generation.

**Table concept:** `embedding_cache`

**Fields:**
- `content_hash` (SHA-256 of input text)
- `embedding_vector`
- `embedding_model`
- `embedding_model_version`
- `dimension`
- `created_at`
- `last_accessed_at`
- `access_count`

**Flow:**
```
content → normalize → hash → cache lookup
                             ├─ hit  → reuse vector, update last_accessed_at
                             └─ miss → generate and persist
```

**Cache eviction policy:**
- LRU (Least Recently Used)
- Entries not accessed in 90 days are archived
- Max cache size: 1M entries

**Acceptance:**
- repeated content does not regenerate embeddings
- embedding cost is bounded
- cache logic is provider-agnostic
- **cache hit rate >80% after warm-up period**

#### 12.3.d — Vector Storage

Store embeddings in PostgreSQL with pgvector or an equivalent vector backend.

**Primary recommendation for V1.0:** PostgreSQL + pgvector

**Rationale:**
- operational simplicity
- fewer moving parts
- consistent with existing persistence posture

**Schema:**
```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE decision_embeddings (
    id BIGSERIAL PRIMARY KEY,
    decision_explanation_id BIGINT REFERENCES decision_explanations(id),
    embedding vector(1536),  -- dimension depends on model
    embedding_model VARCHAR(100),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX ON decision_embeddings 
USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);
```

**Acceptance:**
- vector storage integrated with decision memory
- retrieval latency acceptable (<100ms p95)
- schema versioning documented

#### 12.3.e — Retrieval Service

**Introduce:**
```java
public interface DecisionMemoryService {
    List<DecisionExplanationContext> findSimilarDecisions(
        String query, 
        int topK,
        RetrievalFilters filters
    );
    
    RetrievalQualityMetrics evaluateRetrieval(
        String query,
        List<String> expectedRelevantIds
    );
}
```

**Responsibilities:**
- embedding query generation
- similarity lookup
- top-k retrieval
- context normalization before prompt injection
- **retrieval quality measurement**

**Retrieval filters:**
```java
public record RetrievalFilters(
    List<String> policyVersions,
    List<String> classifications,
    LocalDateTime fromDate,
    LocalDateTime toDate,
    Double minQualityScore
) {}
```

**Acceptance:**
- retrieval logic isolated from advisor logic
- top-k configurable
- retrieval quality testable
- **filtering by metadata supported**

#### 12.3.f — Context Injection into AI Prompts

Before advisory AI invocation:
- retrieve similar decisions
- inject them as bounded context
- prevent context overload

**Constraints:**
- top-k small and controlled (default: 3, max: 10)
- context token budget enforced (max: 2000 tokens)
- no raw unbounded history dump

**Prompt structure:**
```
You are advising on a decision.

CURRENT DECISION (deterministic):
{deterministic_decision}

SIMILAR HISTORICAL DECISIONS:
1. [Policy v2.1] Classification: DEVICE_ERROR, Route: CRITICAL
   Reasoning: Telemetry spike detected in sensor array
   Outcome: Resolved in 2 hours

2. [Policy v2.0] Classification: DEVICE_ERROR, Route: HIGH
   Reasoning: Similar pattern, lower severity
   Outcome: Resolved in 4 hours

3. [Policy v2.1] Classification: NETWORK_ERROR, Route: MEDIUM
   Reasoning: Initially misclassified, corrected to DEVICE_ERROR
   Outcome: Delayed resolution

YOUR TASK:
Provide advisory decision in JSON format...
```

**Acceptance:**
- prompts include curated historical context
- retrieval improves reasoning quality
- token growth remains bounded
- **retrieval context is labeled by policy version**

#### 12.3.g — Dataset Curation Pipeline

Introduce automated and manual curation processes.

**Automated curation rules:**

**Inclusion criteria:**
- Decision confidence >= 0.80
- No validator rejection
- Outcome documented
- Policy version tagged
- Context summary present

**Exclusion criteria:**
- Duplicate decisions (same classification + context hash)
- Decisions with poor outcomes (if outcome tracking enabled)
- Decisions flagged for review
- Test/synthetic decisions

**Quality scoring algorithm:**
```java
double qualityScore = 
    0.4 * confidenceScore +
    0.3 * outcomeSuccessScore +
    0.2 * contextCompletenessScore +
    0.1 * validatorAcceptanceScore;
```

**Manual curation:**
- Human reviewers can flag decisions as "high quality exemplars"
- Exemplars get quality_score = 1.0 and priority in retrieval
- Review dashboard shows:
    - Low-confidence but accepted decisions
    - High-confidence but rejected decisions
    - Decisions with unusual patterns

**Dataset growth management:**

**Pruning strategy:**
- Archive decisions older than 2 years with quality_score < 0.60
- Keep at least 10,000 high-quality decisions per classification type
- Maintain diversity across policy versions

**Refresh frequency:**
- Continuous: automated curation on every decision
- Weekly: quality score recalculation
- Monthly: manual review of edge cases
- Quarterly: dataset health report

**Acceptance:**
- curation rules are explicit and documented
- quality threshold configurable
- dataset growth is bounded
- manual review workflow exists
- **dataset health metrics visible (see 12.7.e)**

---

## 12.4 — Local Model Support

### Goal
Support local AI runtime as an optional mode, as defined in the roadmap.

![ms-roadmap](ms-roadmap)

#### 12.4.a — Ollama Integration

Integrate local model runtime via Ollama.

**Supported examples:**
- llama3
- mistral
- phi

**Configuration:**
```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      models:
        classification: llama3:8b
        embedding: all-MiniLM-L6-v2
```

**Acceptance:**
- local endpoint configurable
- provider selected by environment config
- failures fall back cleanly
- **model pull/update process documented**

#### 12.4.b — Mode Selection

**Configuration modes:**
- `local`
- `cloud`
- `hybrid`

**Meaning:**
- `local` → local provider only
- `cloud` → cloud provider only
- `hybrid` → route according to policy

**Environment-based configuration:**
```yaml
# application-dev.yml
triagemate:
  ai:
    mode: local

# application-prod.yml
triagemate:
  ai:
    mode: hybrid
```

**Acceptance:**
- mode switch is configuration-only
- no code changes required to swap runtime
- local/cloud behavior documented

#### 12.4.c — Hybrid Provider Routing

In hybrid mode, provider selection may depend on:
- event type
- latency sensitivity
- cost budget
- privacy constraints

**Routing policy example:**
```yaml
triagemate:
  ai:
    hybrid-routing:
      - event-type: DEVICE_ERROR
        privacy-level: HIGH
        provider: local
      - event-type: NETWORK_ERROR
        latency-requirement: <100ms
        provider: local
      - event-type: USER_FEEDBACK
        privacy-level: LOW
        provider: cloud
      - default: cloud
```

**Acceptance:**
- routing policy explicit
- selection observable in audit trail
- no hidden provider choice
- **privacy level drives routing**

---

## 12.5 — AI Safety & Governance

### Goal
Make AI bounded, auditable, and operationally safe. The roadmap explicitly includes AI safety gates and audit fields.

![ms-roadmap](ms-roadmap)

#### 12.5.a — Prompt Versioning

Persist both:
- `prompt_version`
- `prompt_hash`

**Rationale:**
- reproducibility
- auditability
- drift analysis
- controlled template evolution

**Acceptance:**
- every AI call references a prompt version
- prompt hash persisted
- prompt changes are reviewable and version-controlled

#### 12.5.b — Input Sanitization

Sanitize user-derived and event-derived text before prompt assembly.

**Threats:**
- prompt injection
- malicious metadata
- control instruction smuggling

**Sanitization rules:**
```java
public class PromptSanitizer {
    public String sanitize(String input) {
        return input
            .replaceAll("(?i)(ignore|disregard|forget).{0,20}(previous|above|instructions)", "[FILTERED]")
            .replaceAll("(?i)system:|assistant:|user:", "[FILTERED]")
            .substring(0, Math.min(input.length(), MAX_INPUT_LENGTH));
    }
}
```

**PII detection and anonymization:**
```java
public String anonymizePII(String input) {
    return input
        .replaceAll("\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b", "[EMAIL]")
        .replaceAll("\\b\\d{3}-\\d{2}-\\d{4}\\b", "[SSN]")
        .replaceAll("\\b\\d{16}\\b", "[CARD]");
}
```

**Acceptance:**
- sanitization rules documented
- dangerous fields normalized or rejected
- no raw free-text blindly injected into prompts
- **PII detection integrated**

#### 12.5.c — Output Validation

Validate AI output before any business use.

**Checks include:**
- schema validity
- allowed enum values
- allowed route targets
- confidence threshold
- override permission scope

**Acceptance:**
- AI output can be rejected cleanly
- rejection reason is auditable
- validator remains deterministic

#### 12.5.d — Cost Controls

Track and enforce:
- input tokens
- output tokens
- cost per call
- cost per decision
- daily AI spend

**Limits:**
- `max_cost_per_decision`: $0.05
- `max_daily_ai_cost`: $100.00
- `max_monthly_ai_cost`: $2000.00

**Cost tracking table:** `ai_cost_tracking`

**Fields:**
- `decision_id`
- `provider`
- `model`
- `input_tokens`
- `output_tokens`
- `cost_usd`
- `timestamp`

**Aggregate queries:**
```sql
-- Daily spend
SELECT DATE(timestamp), SUM(cost_usd) 
FROM ai_cost_tracking 
WHERE timestamp >= CURRENT_DATE 
GROUP BY DATE(timestamp);

-- Cost per classification type
SELECT classification, AVG(cost_usd), COUNT(*)
FROM ai_cost_tracking
JOIN decisions ON decisions.id = ai_cost_tracking.decision_id
GROUP BY classification;
```

**Acceptance:**
- cost tracked on every AI call
- hard stops supported
- budget breaches trigger fallback
- **cost visibility in dashboard (see 12.8)**

#### 12.5.e — Latency Controls

Track and enforce:
- AI request timeout
- maximum acceptable latency
- circuit-breaker thresholds

**Timeout configuration:**
```yaml
triagemate:
  ai:
    timeouts:
      classification: 2s
      advisory: 5s
      embedding: 1s
      rag-retrieval: 500ms
```

**Acceptance:**
- slow AI cannot block core pipeline indefinitely
- timeout leads to fallback
- latency visible in metrics and audit trail

#### 12.5.f — AI Audit Trail

Create a dedicated persistence model for AI interactions.

**Table concept:** `ai_decision_audit`

**Fields:**
- `id`
- `decision_id`
- `provider`
- `model`
- `model_version`
- `prompt_version`
- `prompt_hash`
- `request_payload` (sanitized)
- `response_payload`
- `confidence`
- `latency_ms`
- `input_tokens`
- `output_tokens`
- `cost_usd`
- `accepted_by_validator`
- `rejection_reason`
- `created_at`

**Retention policy:**
- Keep detailed audit for 90 days
- Archive summary (without payloads) for 2 years
- Purge after 2 years unless flagged for long-term retention

**Acceptance:**
- every AI call is traceable
- governance review is possible
- AI influence on decision can be reconstructed
- **retention policy enforced**

#### 12.5.g — Privacy & Compliance

Ensure AI integration complies with GDPR, data residency, and privacy requirements.

**PII Handling Policy:**

**Classification:**
- Level 1: No PII (telemetry, metrics)
- Level 2: Pseudonymized (hashed device IDs)
- Level 3: PII present (user emails, names)

**Routing by privacy level:**
```yaml
triagemate:
  ai:
    privacy-routing:
      level-1: cloud  # OpenAI, Claude allowed
      level-2: cloud-eu  # EU-region providers only
      level-3: local  # local model required
```

**Data residency:**
- EU customers: data stays in EU region
- Provider selection respects `region` configuration
- Audit trail logs selected region

**GDPR compliance:**
- Right to erasure: purge AI audit trail on user deletion
- Right to access: export AI audit trail on request
- Purpose limitation: AI usage limited to decision support
- Data minimization: no full event payload in prompts

**Acceptance:**
- PII classification enforced
- Privacy-level routing tested
- Data residency configurable
- GDPR rights implementable
- **compliance audit report generatable**

---

## 12.6 — Minimal Concurrency and Runtime Sanity

### Goal
Ensure AI integration does not destabilize the existing event-driven pipeline.

The roadmap explicitly requires minimal concurrency sanity.

![ms-roadmap](ms-roadmap)

#### 12.6.a — Dedicated AI Executor

AI calls must not run on Kafka consumer threads.

**Introduce dedicated execution resources:**
- AI thread pool
- bounded queue
- backpressure behavior

**Configuration:**
```java
@Configuration
public class AiExecutorConfig {
    @Bean
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-advisor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

**Backpressure behavior:**
- Queue full → reject with `AiOverloadException`
- Exception triggers deterministic fallback
- Metrics track queue depth and rejection rate

**Acceptance:**
- consumer threads are not starved by AI latency
- AI load isolation is explicit
- executor sizing documented
- **backpressure tested**

#### 12.6.b — Circuit Breaker

Use Resilience4j or equivalent around provider calls.

**Configuration:**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      aiProvider:
        failure-rate-threshold: 50
        slow-call-rate-threshold: 50
        slow-call-duration-threshold: 3s
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 5
        sliding-window-size: 10
```

**Responsibilities:**
- open on repeated failures
- fast-fail while open
- protect pipeline stability

**Acceptance:**
- AI provider instability does not cascade
- fallback path engaged automatically
- breaker state observable
- **circuit breaker state exposed in health endpoint**

#### 12.6.c — Graceful Degradation

If AI subsystem degrades:
- deterministic classification still works
- deterministic routing still works
- final decision still publishes

**Degradation scenarios:**

| Scenario | Impact | Behavior |
|----------|--------|----------|
| AI timeout | Advisory unavailable | Deterministic decision only |
| Circuit breaker open | Advisory disabled | Deterministic decision only |
| Cost budget exceeded | Advisory paused | Deterministic decision only |
| Embedding service down | RAG unavailable | Advisory without historical context |
| Local model unavailable | Hybrid routing adjusts | Cloud provider used |

**Acceptance:**
- no total system outage due to AI outage
- degradation mode tested
- audit trail records degraded mode
- **degradation scenarios documented and tested**

---

## 12.7 — AI Quality Metrics & Measurement

### Goal
Make AI subsystem quality quantifiable, measurable, and improvable through data-driven KPIs.

#### 12.7.a — Core Quality Metrics

Define measurable KPIs for AI subsystem performance.

**Classification Quality:**
- `precision`: correct AI classifications / total AI classifications
- `recall`: correct AI classifications / total actual class instances
- `f1_score`: harmonic mean of precision and recall
- `accuracy`: correct classifications / total classifications

**Advisory Quality:**
- `advisor_acceptance_rate`: accepted AI advice / total AI advice given
- `advisor_value_add`: decisions improved by AI / total AI advice accepted
- `false_positive_override_rate`: incorrect AI overrides / total overrides

**RAG Quality:**
- `retrieval_precision@k`: relevant retrievals in top-k / k
- `retrieval_recall@k`: relevant retrievals in top-k / total relevant
- `context_utilization_rate`: retrieved context used in reasoning / total retrieved

**Operational Quality:**
- `availability`: successful AI calls / total AI calls
- `latency_p50`, `latency_p95`, `latency_p99`
- `cost_per_decision`: average AI cost per decision
- `fallback_rate`: fallback invocations / total invocations

**Target SLIs (Service Level Indicators):**
```yaml
triagemate:
  ai:
    quality-targets:
      classification:
        f1_score: >= 0.85
        precision: >= 0.90
        recall: >= 0.80
      advisory:
        acceptance_rate: >= 0.70
        false_positive_rate: <= 0.10
      rag:
        retrieval_precision_at_3: >= 0.75
      operational:
        availability: >= 0.995
        latency_p95: <= 1000ms
        fallback_rate: <= 0.05
```

**Acceptance:**
- metrics definitions documented
- target SLIs defined
- measurement automated

#### 12.7.b — Metrics Collection Pipeline

Implement automated metrics collection and aggregation.

**Metrics table:** `ai_quality_metrics`

**Fields:**
- `metric_name`
- `metric_value`
- `metric_type` (gauge, counter, histogram)
- `labels` (JSON: model, provider, classification_type, etc.)
- `timestamp`
- `aggregation_period` (1h, 1d, 1w)

**Collection triggers:**
- Real-time: every AI decision
- Hourly: aggregated quality metrics
- Daily: quality report generation
- Weekly: trend analysis and alerting

**Example queries:**
```sql
-- Hourly advisor acceptance rate
SELECT 
    DATE_TRUNC('hour', timestamp) AS hour,
    AVG(metric_value) AS acceptance_rate
FROM ai_quality_metrics
WHERE metric_name = 'advisor_acceptance_rate'
  AND timestamp >= NOW() - INTERVAL '7 days'
GROUP BY hour
ORDER BY hour;

-- Classification F1 by model version
SELECT 
    labels->>'model_version' AS model_version,
    AVG(metric_value) AS avg_f1
FROM ai_quality_metrics
WHERE metric_name = 'classification_f1_score'
  AND timestamp >= NOW() - INTERVAL '30 days'
GROUP BY model_version;
```

**Acceptance:**
- metrics persisted automatically
- aggregation queries performant
- historical trends queryable

#### 12.7.c — Confidence Calibration System

Implement calibration to ensure AI confidence scores are trustworthy.

**Calibration concept:**
- If AI says "85% confident", it should be correct ~85% of the time
- Track actual accuracy vs. predicted confidence
- Adjust confidence interpretation over time

**Calibration table:** `ai_confidence_calibration`

**Fields:**
- `confidence_bucket` (0.0-0.1, 0.1-0.2, ..., 0.9-1.0)
- `predicted_confidence` (bucket midpoint)
- `actual_accuracy` (empirical correctness rate)
- `sample_count`
- `model_version`
- `classification_type`
- `calibration_period_start`
- `calibration_period_end`

**Calibration process:**
1. Collect AI predictions with confidence scores
2. Validate outcomes (was AI correct?)
3. Bucket by confidence range
4. Calculate actual accuracy per bucket
5. Compare predicted vs. actual
6. Adjust confidence thresholds if miscalibrated

**Example calibration data:**
```
| Confidence Bucket | Predicted | Actual Accuracy | Sample Count | Calibrated? |
|-------------------|-----------|-----------------|--------------|-------------|
| 0.90 - 1.00       | 0.95      | 0.93            | 1247         | ✓ Good      |
| 0.80 - 0.90       | 0.85      | 0.78            | 892          | ✗ Overconfident |
| 0.70 - 0.80       | 0.75      | 0.74            | 634          | ✓ Good      |
```

**Threshold adjustment:**
- If 0.80-0.90 bucket shows only 78% actual accuracy
- Raise acceptance threshold from 0.85 to 0.88
- Or apply calibration scaling function

**Acceptance:**
- calibration data collected automatically
- miscalibration detected and alerted
- thresholds adjusted based on empirical data
- **calibration report generated weekly**

#### 12.7.d — A/B Testing Framework

Enable rigorous testing of prompt versions, model versions, and configuration changes.

**A/B test configuration:**
```yaml
triagemate:
  ai:
    ab-tests:
      - name: "prompt-v1.1-classification"
        enabled: true
        variant-a:
          prompt-version: "1.0.0"
          traffic-percentage: 50
        variant-b:
          prompt-version: "1.1.0"
          traffic-percentage: 50
        duration-days: 7
        success-metric: "classification_f1_score"
        minimum-improvement: 0.03
```

**Traffic splitting:**
- Hash-based routing (stable per decision_id)
- Even distribution across variants
- No cross-contamination

**Metrics comparison:**
```sql
-- Compare variants
SELECT 
    variant,
    AVG(f1_score) AS avg_f1,
    STDDEV(f1_score) AS stddev_f1,
    COUNT(*) AS sample_size
FROM ai_ab_test_results
WHERE test_name = 'prompt-v1.1-classification'
  AND test_start_date >= '2025-03-10'
GROUP BY variant;
```

**Statistical significance:**
- Use t-test for metric comparison
- Require p-value < 0.05
- Require minimum sample size (n >= 100 per variant)

**Decision criteria:**
```
if (variant_b.f1_score > variant_a.f1_score + minimum_improvement 
    AND p_value < 0.05 
    AND sample_size >= 100):
    promote variant_b to production
else:
    keep variant_a
```

**Acceptance:**
- A/B tests configurable
- traffic splitting correct
- statistical comparison automated
- **winner selection based on data, not intuition**

#### 12.7.e — Dataset Health Monitoring

Monitor the quality and characteristics of the curated decision explanation dataset.

**Dataset health metrics:**

**Coverage:**
- `decisions_per_classification`: distribution of examples across classes
- `policy_version_coverage`: representation of policy versions
- `temporal_coverage`: time range span of dataset

**Quality:**
- `average_quality_score`: mean quality score of dataset
- `high_quality_percentage`: % with quality_score >= 0.85
- `diversity_score`: measure of context variety

**Growth:**
- `dataset_size`: total curated decisions
- `weekly_growth_rate`: new additions per week
- `archival_rate`: decisions archived per week

**Health thresholds:**
```yaml
triagemate:
  ai:
    dataset-health:
      min-decisions-per-classification: 1000
      min-high-quality-percentage: 0.60
      max-age-imbalance-ratio: 5.0  # oldest to newest ratio
```

**Alerts:**
- "DEVICE_ERROR classification has only 347 examples (target: 1000)"
- "Dataset quality score dropped to 0.68 (target: >= 0.75)"
- "No decisions from policy v2.3 in dataset (current production version)"

**Acceptance:**
- dataset health tracked continuously
- alerts trigger on threshold violations
- **monthly dataset health report generated**

#### 12.7.f — Quality Regression Detection

Automatically detect when AI quality degrades.

**Regression detection algorithm:**
1. Establish baseline metrics (7-day moving average)
2. Monitor current metrics (24-hour window)
3. Compare current to baseline
4. Alert if degradation exceeds threshold

**Regression thresholds:**
```yaml
triagemate:
  ai:
    regression-detection:
      f1_score_drop: 0.05  # alert if drops >5%
      acceptance_rate_drop: 0.10
      latency_increase: 0.50  # alert if increases >50%
      cost_increase: 0.20
```

**Alert examples:**
- "Classification F1 score dropped from 0.89 to 0.82 in last 24h"
- "Advisor acceptance rate dropped from 72% to 58%"
- "RAG retrieval precision@3 dropped from 0.78 to 0.65"

**Automated response:**
- Regression detected → create incident ticket
- If severe (>10% drop) → trigger circuit breaker
- If critical (>20% drop) → switch to deterministic-only mode
- Notify on-call engineer

**Acceptance:**
- regression detection automated
- alerts actionable
- severe regression triggers automatic mitigation
- **regression incidents tracked and analyzed**

---

## 12.8 — Observability & Monitoring

### Goal
Make AI subsystem behavior observable, debuggable, and operationally manageable.

#### 12.8.a — AI-Specific Dashboards

Create dedicated dashboards for AI subsystem monitoring.

**Dashboard 1: AI Operations Overview**
- Real-time metrics:
    - AI calls/minute
    - Success rate
    - Fallback rate
    - Average latency
    - Circuit breaker state
- Cost tracking:
    - Hourly spend
    - Daily spend vs. budget
    - Cost per decision
    - Token usage

**Dashboard 2: AI Quality Metrics**
- Classification performance:
    - F1 score trend (7-day)
    - Precision/recall by class
    - Confusion matrix
- Advisory performance:
    - Acceptance rate trend
    - Override outcomes
    - Validator rejection reasons
- RAG performance:
    - Retrieval precision@k
    - Embedding cache hit rate
    - Context utilization

**Dashboard 3: AI Model & Prompt Health**
- Model versions in use
- Prompt versions in use
- A/B test progress
- Calibration status
- Model drift alerts

**Dashboard 4: Dataset Health**
- Dataset size and growth
- Quality score distribution
- Coverage by classification
- Archival/pruning activity

**Technology:**
- Grafana dashboards
- Prometheus metrics
- Custom queries on `ai_quality_metrics` table

**Acceptance:**
- dashboards accessible to ops team
- real-time data (<30s latency)
- historical data queryable
- **dashboards tested with realistic load**

#### 12.8.b — Health Endpoints

Expose AI subsystem health via Spring Boot Actuator.

**Endpoint:** `/actuator/health/ai`

**Response structure:**
```json
{
  "status": "UP",
  "components": {
    "aiProvider": {
      "status": "UP",
      "details": {
        "provider": "openai",
        "model": "gpt-4o-mini",
        "circuitBreaker": "CLOSED",
        "lastSuccessfulCall": "2025-03-10T14:32:11Z"
      }
    },
    "embeddingService": {
      "status": "UP",
      "details": {
        "cacheHitRate": 0.84,
        "avgLatencyMs": 45
      }
    },
    "ragRetrieval": {
      "status": "UP",
      "details": {
        "vectorDbConnected": true,
        "avgRetrievalLatencyMs": 87
      }
    },
    "qualityMetrics": {
      "status": "UP",
      "details": {
        "currentF1Score": 0.87,
        "targetF1Score": 0.85,
        "regressionDetected": false
      }
    }
  }
}
```

**Health check logic:**
- `DOWN` if circuit breaker open
- `DOWN` if cost budget exceeded
- `DOWN` if quality regression >20%
- `UP` otherwise

**Acceptance:**
- health endpoint responds <500ms
- status accurately reflects subsystem state
- integrated with monitoring alerts

#### 12.8.c — Alerting Rules

Define actionable alerts for AI subsystem issues.

**Critical Alerts (page on-call):**
- Circuit breaker opened for >5 minutes
- F1 score regression >20%
- Cost budget exceeded
- AI subsystem unavailable
- PII leak detected in audit trail

**Warning Alerts (ticket created):**
- F1 score regression >5%
- Acceptance rate drop >10%
- Latency p95 >2s
- Fallback rate >10%
- Embedding cache hit rate <70%
- Dataset quality score <0.70

**Info Alerts (logged):**
- New model version deployed
- New prompt version deployed
- A/B test completed
- Weekly quality report generated

**Alert channels:**
- Critical → PagerDuty
- Warning → Slack #triagemate-ai-alerts
- Info → Slack #triagemate-ai-info

**Acceptance:**
- alerts routed to correct channels
- alert fatigue minimized (actionable alerts only)
- alert runbooks documented

#### 12.8.d — Distributed Tracing

Integrate AI calls into distributed tracing system.

**Technology:** OpenTelemetry + Jaeger/Zipkin

**Trace structure:**
```
DecisionPipeline [parent span]
  ├─ DeterministicClassifier [span]
  ├─ DeterministicPolicy [span]
  ├─ AiDecisionAdvisor [span]
  │   ├─ RAG Retrieval [span]
  │   │   ├─ Embedding Generation [span]
  │   │   └─ Vector Search [span]
  │   ├─ Prompt Assembly [span]
  │   ├─ Provider API Call [span]
  │   └─ Response Parsing [span]
  ├─ DecisionValidator [span]
  └─ EventPublisher [span]
```

**Trace attributes:**
- `ai.provider`: openai/claude/ollama
- `ai.model`: model name
- `ai.prompt_version`: prompt version
- `ai.confidence`: AI confidence score
- `ai.cost_usd`: call cost
- `ai.tokens.input`: input tokens
- `ai.tokens.output`: output tokens

**Acceptance:**
- AI calls visible in trace UI
- end-to-end latency breakdown available
- trace sampling configured (<10% in prod)

#### 12.8.e — Audit Trail Query Interface

Provide query interface for governance and debugging.

**Use cases:**
- "Show all AI decisions for decision_id X"
- "Find all rejected AI advice in last 7 days"
- "Show prompt evolution for classification_type Y"
- "Retrieve all AI calls with confidence <0.70 that were accepted"

**Query API:**
```java
public interface AiAuditQueryService {
    List<AiAuditRecord> findByDecisionId(Long decisionId);
    List<AiAuditRecord> findRejectedAdvice(LocalDateTime from, LocalDateTime to);
    List<AiAuditRecord> findByPromptVersion(String promptVersion);
    List<AiAuditRecord> findLowConfidenceAccepted(double maxConfidence);
    AuditReport generateComplianceReport(LocalDateTime from, LocalDateTime to);
}
```

**Compliance report contents:**
- Total AI calls
- Acceptance/rejection breakdown
- Cost summary
- Model versions used
- Prompt versions used
- PII handling incidents (should be 0)
- Data residency compliance (all calls in correct region)

**Acceptance:**
- queries performant (<2s for 30-day range)
- compliance report generatable on demand
- **report suitable for external audit**

---

## 12.7 — AI Quality Metrics & Testing Strategy

### Goal
Establish measurable quality standards for AI components and define comprehensive testing approaches to ensure AI advice quality, retrieval effectiveness, and system reliability over time.

#### 12.7.a — RAG Quality Metrics

**Retrieval Precision & Recall**

Measure retrieval effectiveness:
- **Precision@K**: percentage of retrieved decisions actually relevant to query
- **Recall@K**: percentage of relevant decisions successfully retrieved
- **MRR (Mean Reciprocal Rank)**: position of first relevant result

**Target thresholds:**
- Precision@5 ≥ 0.80
- Recall@5 ≥ 0.70
- MRR ≥ 0.85

**Measurement approach:**
- Maintain labeled test set of query-decision pairs
- Periodic automated evaluation against test set
- Manual spot-checking on production queries

**Acceptance:**
- retrieval quality metrics tracked
- degradation alerts configured
- metrics visible in monitoring dashboard

#### 12.7.b — AI Advice Quality Metrics

**Advisor Acceptance Rate**

Track how often AI advice is accepted vs rejected by validator:
- Overall acceptance rate
- Acceptance rate by classification type
- Acceptance rate by confidence band
- Acceptance rate by provider/model

**Target threshold:**
- Overall acceptance rate ≥ 0.60 (if lower, AI is providing low-value noise)

**Advice Impact Metrics**

When AI advice is accepted, measure:
- Decision override rate (AI changed deterministic outcome)
- Override correctness (via manual audit sample)
- Time-to-decision improvement
- Explanation quality score (manual assessment)

**Acceptance:**
- advice acceptance tracked per decision
- weekly/monthly aggregates computed
- trend analysis for degradation detection

#### 12.7.c — Confidence Calibration

**Calibration Validation**

AI confidence scores must be calibrated:
- If AI says confidence=0.80, it should be correct ~80% of time
- Track actual correctness vs predicted confidence
- Compute Expected Calibration Error (ECE)

**Calibration bins:**
```
[0.0-0.2]: very low confidence
[0.2-0.4]: low confidence
[0.4-0.6]: medium confidence
[0.6-0.8]: high confidence
[0.8-1.0]: very high confidence
```

**Target:**
- ECE ≤ 0.10 (well-calibrated)
- Per-bin accuracy within ±0.15 of bin midpoint

**Recalibration trigger:**
- If ECE > 0.15, recalibration required
- Adjust confidence thresholds or retrain

**Acceptance:**
- calibration metrics computed monthly
- calibration curve visualized
- recalibration process documented

#### 12.7.d — Regression Testing Strategy

**AI Response Stability**

Prevent silent degradation:
- Maintain golden test set (input → expected output pairs)
- Run against golden set on every model/prompt change
- Track delta in responses

**Regression test categories:**
1. **Classification accuracy tests** (100+ cases)
    - Known events with verified classifications
    - Edge cases and ambiguous scenarios
    - Historical misclassifications

2. **RAG retrieval tests** (50+ queries)
    - Known query → expected top-k decisions
    - Semantic similarity verification
    - Noise resistance tests

3. **Advice validation tests** (50+ scenarios)
    - Valid advice acceptance
    - Invalid advice rejection
    - Edge case handling

**Regression detection:**
- Classification accuracy drop > 5% → BLOCK
- Retrieval MRR drop > 0.10 → BLOCK
- Advice acceptance drop > 15% → INVESTIGATE

**Acceptance:**
- regression test suite automated
- executed on every prompt/model change
- results block deployment if thresholds breached

#### 12.7.e — A/B Testing Framework

**Prompt/Model Comparison**

Enable safe experimentation:
- Route subset of traffic to variant (new prompt/model)
- Track quality metrics for control vs variant
- Statistical significance testing before rollout

**A/B test structure:**
```
Control:   90% traffic, current prompt v1.2
Variant A: 5% traffic, candidate prompt v1.3
Variant B: 5% traffic, alternative model
```

**Decision criteria:**
- Variant must show statistical improvement (p < 0.05)
- No degradation in safety metrics
- Cost/latency within acceptable range
- Minimum 1000 samples per variant

**Acceptance:**
- A/B framework supports parallel execution
- traffic routing configurable
- statistical analysis automated

#### 12.7.f — Synthetic Adversarial Testing

**Robustness Validation**

Test AI resilience against adversarial inputs:
- Prompt injection attempts
- Malformed event payloads
- Extreme confidence edge cases
- Gibberish/noise inputs

**Test categories:**
1. **Security tests**: injection, smuggling, jailbreak attempts
2. **Robustness tests**: malformed JSON, encoding issues, size limits
3. **Edge case tests**: empty fields, null values, extreme numbers
4. **Noise tests**: random strings, non-semantic content

**Expected behavior:**
- Security threats → sanitization catches or AI rejects
- Malformed inputs → validation catches before AI call
- Edge cases → graceful degradation to fallback
- Noise → low confidence + rejection by validator

**Acceptance:**
- adversarial test suite defined
- executed in CI pipeline
- no security bypasses
- 100% graceful handling (no crashes)

#### 12.7.g — Production Quality Monitoring

**Real-time Quality Dashboard**

Monitor AI health in production:

**Operational metrics:**
- Request rate (req/sec)
- Success rate
- Fallback rate
- Average latency (p50, p95, p99)
- Cost per decision
- Provider availability

**Quality metrics:**
- Advice acceptance rate (hourly)
- Confidence distribution
- Override rate
- Validation rejection rate
- Retrieval hit rate

**Alerting thresholds:**
- Success rate < 95% → WARNING
- Success rate < 90% → CRITICAL
- Acceptance rate drops > 20% from baseline → INVESTIGATE
- Latency p95 > timeout threshold → WARNING
- Cost per decision > budget → THROTTLE

**Acceptance:**
- dashboard provides real-time visibility
- alerts route to operations team
- historical trend analysis available

---

## 12.8 — Prompt Engineering Lifecycle

### Goal
Establish governance and process for prompt template evolution, ensuring changes are controlled, validated, and reversible.

#### 12.8.a — Prompt Template Repository

**Version Control**

All prompts stored in dedicated repository:
```
/prompts
  /classification
    v1.0-classify-event.txt
    v1.1-classify-event.txt
    v2.0-classify-event-with-context.txt
  /advisory
    v1.0-suggest-override.txt
  /rag
    v1.0-summarize-similar-decisions.txt
```

**Prompt metadata:**
```yaml
version: "1.1"
purpose: "Event classification"
model_target: "gpt-4o-mini"
created: "2025-01-15"
author: "gabriele"
approved_by: "tech_lead"
status: "active"
replaces: "1.0"
hash: "sha256:abc123..."
```

**Acceptance:**
- prompts version-controlled in Git
- metadata enforced for every template
- history fully auditable

#### 12.8.b — Prompt Approval Process

**Lifecycle stages:**
```
DRAFT → REVIEW → APPROVED → ACTIVE → DEPRECATED
```

**Approval workflow:**
1. Author creates prompt in DRAFT
2. Run against regression test suite
3. Run A/B test if modifying ACTIVE prompt
4. Tech lead reviews results + security implications
5. Approval moves to APPROVED
6. Deployment moves to ACTIVE
7. Old prompt moves to DEPRECATED after grace period

**Approval criteria:**
- Regression tests pass
- A/B test shows improvement or no degradation
- Security review confirms no injection risks
- Cost impact assessed and acceptable

**Acceptance:**
- approval workflow enforced
- no prompt goes to production without approval
- approval chain auditable

#### 12.8.c — Prompt A/B Testing Protocol

**Test execution:**
1. Deploy candidate prompt as variant
2. Route 5-10% traffic to variant
3. Collect minimum 1000 samples
4. Run statistical comparison
5. Decision: rollout, iterate, or abandon

**Metrics compared:**
- Classification accuracy
- Advice acceptance rate
- Confidence calibration
- Latency
- Cost per call

**Rollout decision:**
- If variant superior (p < 0.05) → gradual rollout (10% → 50% → 100%)
- If equivalent → decision based on cost/latency
- If inferior → abandon and iterate

**Acceptance:**
- A/B protocol documented
- test execution automated
- rollout gradual and reversible

#### 12.8.d — Prompt Rollback Strategy

**Instant Rollback Capability**

If production prompt degrades:
1. Detect degradation via quality metrics
2. Trigger rollback to previous ACTIVE version
3. Route 100% traffic to stable version
4. Investigate root cause
5. Fix and re-test before re-deploying

**Rollback triggers:**
- Advice acceptance rate drops > 30% from baseline
- Validation rejection rate spikes > 2x baseline
- Cost per decision exceeds budget by > 50%
- Latency p95 > 2x baseline
- Manual trigger by operations team

**Rollback SLA:**
- Detection to rollback complete: < 5 minutes
- Automated rollback on critical thresholds
- Manual rollback available 24/7

**Acceptance:**
- rollback procedure tested quarterly
- rollback can execute without code deploy
- previous version always hot-standby

#### 12.8.e — Prompt Security Review

**Injection Risk Assessment**

Every prompt reviewed for:
- User input sanitization points
- Control instruction separation
- Output validation robustness
- Adversarial resilience

**Security checklist:**
- [ ] User input clearly delimited in prompt
- [ ] Instructions use clear boundaries (e.g., XML tags)
- [ ] No direct interpolation of user text into instructions
- [ ] Output schema validation catches malicious responses
- [ ] Tested against OWASP LLM Top 10 attacks

**Acceptance:**
- security review mandatory for APPROVED status
- security test suite exists
- periodic re-review (quarterly)

---

## 12.9 — Model Versioning & Lifecycle

### Goal
Manage model versions, provider migrations, and model drift systematically to ensure long-term system stability and quality.

#### 12.9.a — Model Version Tracking

**Persist model details:**

In `ai_decision_audit` table:
- `provider` (e.g., "openai", "anthropic", "ollama")
- `model` (e.g., "gpt-4o-mini", "claude-sonnet-4")
- `model_version` (e.g., "gpt-4o-mini-2024-07-18")
- `api_version` (e.g., "v1")

**Configuration:**
```yaml
spring:
  ai:
    openai:
      model: "gpt-4o-mini"
      model-version: "2024-07-18"  # explicit version pinning
```

**Rationale:**
- Providers deprecate models (e.g., GPT-3.5 → GPT-4)
- Model updates change behavior even with same name
- Audit trail must capture exact model used

**Acceptance:**
- model version tracked per decision
- version changes visible in audit
- version pinning supported in config

#### 12.9.b — Model Drift Detection

**Behavioral Drift Monitoring**

Track model response stability over time:
- Run golden test set weekly
- Compare responses to baseline
- Detect semantic drift in classifications/advice

**Drift metrics:**
- Classification agreement rate vs baseline (target: > 95%)
- Confidence distribution shift (KL divergence < 0.1)
- Advice content similarity (embedding distance)

**Drift triggers:**
- Agreement rate < 90% → INVESTIGATE
- Agreement rate < 85% → ALERT
- Agreement rate < 80% → CONSIDER MODEL LOCK or MIGRATION

**Acceptance:**
- drift detection runs automatically
- baseline updated only on intentional model change
- drift alerts route to AI team

#### 12.9.c — Model Migration Strategy

**Planned Migration Workflow**

When provider releases new model or deprecates old:

1. **Evaluation phase:**
    - Run new model on regression test suite
    - Run A/B test (small traffic %)
    - Compare quality, cost, latency

2. **Decision:**
    - If superior → plan migration
    - If equivalent → evaluate cost/latency trade-off
    - If inferior → stay on current or seek alternative

3. **Migration:**
    - Gradual rollout (5% → 10% → 25% → 50% → 100%)
    - Monitor quality metrics at each step
    - Rollback capability maintained throughout

4. **Validation:**
    - Post-migration quality check (48h monitoring)
    - Update baseline for drift detection
    - Document migration in ADR

**Migration triggers:**
- Provider deprecation notice
- Cost optimization opportunity
- Quality improvement opportunity
- New capability requirement

**Acceptance:**
- migration process documented
- migration execution gradual
- rollback always possible

#### 12.9.d — Model Deprecation Handling

**Provider Deprecation Response**

When provider announces model deprecation:

**Timeline:**
- T-90 days: identify deprecation
- T-60 days: evaluate replacement models
- T-45 days: begin A/B testing
- T-30 days: migration decision
- T-15 days: begin gradual rollout
- T-0 days: deprecation date, migration complete

**Fallback if migration blocked:**
- Revert to deterministic-only mode
- No AI advice until replacement secured
- System remains operational

**Acceptance:**
- deprecation monitoring process exists
- response timeline documented
- deterministic fallback validated

#### 12.9.e — Multi-Model Strategy

**Provider Diversification**

Support multiple models simultaneously:
- Primary: OpenAI GPT-4o-mini (cloud, fast, cost-effective)
- Secondary: Anthropic Claude Sonnet (cloud, high-quality reasoning)
- Tertiary: Ollama Llama3 (local, privacy-sensitive use cases)

**Routing logic:**
```
if (event.requiresPrivacy()) → local model
else if (event.requiresHighQuality()) → Claude
else → GPT-4o-mini (default)
```

**Acceptance:**
- multiple models configurable
- routing policy explicit
- fallback chain defined (primary → secondary → deterministic)

---

## 12.10 — RAG Dataset Curation Pipeline

### Goal
Define how the curated decision explanation dataset is built, maintained, and evolved to ensure high-quality semantic retrieval.

#### 12.10.a — Curation Criteria

**Inclusion criteria:**

A decision is curated into the explanation dataset if:
- Decision reached COMPLETED state
- Contains meaningful decision_reason (not generic/template)
- Policy version is active or recent (< 6 months old)
- Classification confidence > 0.70 (if AI-assisted)
- Not flagged as erroneous or disputed

**Exclusion criteria:**
- Incomplete decisions
- Generic/template reasons ("default rule applied")
- Test/synthetic events
- Decisions from deprecated policies (> 6 months old)
- Manually flagged as poor quality

**Acceptance:**
- curation criteria documented
- applied automatically via batch job
- manual override possible with justification

#### 12.10.b — Curation Process

**Automated Pipeline**

Daily batch job:
1. Query completed decisions from last 24h
2. Apply inclusion/exclusion filters
3. Extract and normalize fields:
    - decision_id
    - policy_version
    - classification
    - route
    - outcome
    - decision_reason (normalized)
    - decision_context_summary (generated)
4. Insert into `decision_explanations` table
5. Trigger embedding generation

**Context summarization:**

Generate concise context summary from event payload:
- Extract key fields (device_id, error_code, severity, etc.)
- Normalize to consistent format
- Remove PII and sensitive data
- Limit to 500 chars

**Acceptance:**
- curation runs daily
- failures do not block main decision pipeline
- curation metrics tracked (curated count, rejection reasons)

#### 12.10.c — Dataset Quality Control

**Quality Checks**

Periodic review:
- Random sample manual review (50 decisions/month)
- Check reason quality and relevance
- Identify template/generic reasons slipping through
- Validate PII removal

**Quality metrics:**
- % decisions curated (target: 60-80% of completed decisions)
- Avg reason length (target: 100-500 chars)
- Duplicate/near-duplicate rate (target: < 5%)
- Manual quality score (target: > 4/5)

**Acceptance:**
- quality review process documented
- quality metrics tracked monthly
- remediation process for quality issues

#### 12.10.4 — Dataset Growth Management

**Pruning Strategy**

As dataset grows, manage size:

**Time-based pruning:**
- Archive decisions from policies deprecated > 1 year
- Remove decisions older than 2 years (configurable)

**Quality-based pruning:**
- Remove lowest-quality explanations if dataset > size limit
- Quality score based on:
    - Retrieval frequency (how often retrieved in RAG)
    - Manual quality rating
    - Semantic uniqueness

**Size targets:**
- Phase 12 V1: no hard limit (< 100K decisions expected)
- Phase 14 (production scale): 500K-1M decisions, active pruning

**Acceptance:**
- pruning strategy documented
- archival before deletion
- pruning configurable and reversible

#### 12.10.e — Dataset Refresh & Versioning

**Dataset Versioning**

Track dataset evolution:
- `dataset_version` in config
- Version incremented on major curation logic change
- Embeddings tagged with dataset version

**Refresh scenarios:**
- Policy overhaul → re-curate all recent decisions
- Curation logic change → re-curate and re-embed
- Quality issue discovered → targeted re-curation

**Refresh process:**
1. Identify decisions to refresh
2. Re-run curation logic
3. Regenerate embeddings (cache invalidation)
4. Update dataset_version
5. Validate retrieval quality post-refresh

**Acceptance:**
- dataset versioning implemented
- refresh process automated
- refresh does not cause downtime

---

## 12.11 — Privacy, Data Residency & Compliance

### Goal
Ensure AI integration complies with privacy regulations, handles PII appropriately, and respects data residency requirements.

#### 12.11.a — PII Handling in Prompts

**PII Identification & Scrubbing**

Before sending data to AI providers:
1. Identify PII fields in event payload:
    - email, phone, IP address, device_id, user_id, etc.
2. Scrub or pseudonymize:
    - Replace with placeholders (e.g., `<EMAIL>`, `<DEVICE_ID>`)
    - Or use one-way hash for device_id/user_id if needed for context
3. Include only anonymized context in prompts

**Example:**
```
Original event:
{
  "device_id": "ABC123",
  "user_email": "user@example.com",
  "error_code": "E404"
}

Scrubbed for AI:
{
  "device_id": "<DEVICE_ID>",
  "user_email": "<EMAIL>",
  "error_code": "E404"
}
```

**Acceptance:**
- PII scrubbing applied before all AI calls
- scrubbing rules configurable
- scrubbing tested with synthetic PII

#### 12.11.b — Data Residency Requirements

**Regional Data Constraints**

For EU customers or privacy-sensitive deployments:
- Use EU-region AI endpoints (e.g., OpenAI EU, Anthropic EU if available)
- Or use local models (Ollama) to keep data on-premises

**Configuration:**
```yaml
spring:
  ai:
    data-residency: "EU"  # EU, US, LOCAL
    openai:
      endpoint: "https://api.openai.com/eu/v1"  # hypothetical EU endpoint
```

**Routing logic:**
```
if (deployment.region == "EU" && !provider.supportsEU()) {
  → fallback to local model or deterministic
}
```

**Acceptance:**
- data residency configurable
- provider selection respects residency constraints
- fallback to local/deterministic if no compliant provider

#### 12.11.c — GDPR Compliance

**Right to Erasure**

If user requests data deletion:
1. Delete from `ai_decision_audit` (or anonymize decision_id linkage)
2. Remove from `decision_explanations` curated dataset
3. Invalidate embeddings in `embedding_cache`
4. Re-index remaining dataset

**Data Minimization**

Store only necessary data:
- AI audit: retain 90 days (configurable)
- Decision explanations: retain 2 years (configurable)
- Embeddings: retain as long as source decision explanation

**Acceptance:**
- deletion process documented and tested
- retention periods configurable
- audit logs for deletion actions

#### 12.11.d — AI Provider Data Policies

**Provider Contract Review**

Ensure AI provider agreements cover:
- No training on customer data (zero data retention)
- Data not shared with third parties
- Compliance with GDPR/CCPA
- Data residency commitments

**OpenAI:**
- Use API with "zero data retention" option

**Anthropic:**
- Enterprise agreement with data protection clauses

**Ollama (local):**
- No data leaves infrastructure (highest privacy)

**Acceptance:**
- provider agreements reviewed
- data protection clauses verified
- documented in ADR

#### 12.11.e — Audit Log Retention

**Compliance-Driven Retention**

`ai_decision_audit` table retention:
- Operational: 90 days (for debugging)
- Compliance: extended to 1 year if required by regulation
- Archival: move to cold storage after retention period

**Anonymization option:**
- After operational period, anonymize PII-linked fields
- Retain anonymized records for analytics

**Acceptance:**
- retention policy configurable
- archival process automated
- anonymization tested

---

## 12.12 — Error Taxonomy & Retry Logic

### Goal
Classify AI errors systematically and apply appropriate retry/fallback strategies based on error type.

#### 12.12.a — Error Classification

**Error Categories**

1. **Transient errors** (retryable):
    - Network timeout
    - HTTP 429 (rate limit)
    - HTTP 503 (service unavailable)
    - Provider temporary overload

2. **Permanent errors** (not retryable):
    - HTTP 401 (authentication failure)
    - HTTP 400 (malformed request)
    - Schema validation failure
    - Provider returns invalid response

3. **Degraded quality** (retryable once):
    - Low confidence response
    - Response parsing ambiguity
    - Unexpected response structure

4. **Cost/latency exceeded** (no retry):
    - Cost budget exceeded
    - Latency timeout exceeded
    - Circuit breaker open

**Acceptance:**
- every AI error classified into category
- category determines retry/fallback behavior
- error classification auditable

#### 12.12.b — Retry Strategy

**Retry Logic by Category**

| Error Category | Retry Count | Backoff | Fallback |
|----------------|-------------|---------|----------|
| Transient | 3 | Exponential (1s, 2s, 4s) | Deterministic |
| Permanent | 0 | None | Deterministic |
| Degraded Quality | 1 | Immediate | Deterministic |
| Cost/Latency | 0 | None | Deterministic |

**Retry implementation:**
```java
@Retryable(
  value = {TransientAiException.class},
  maxAttempts = 3,
  backoff = @Backoff(delay = 1000, multiplier = 2)
)
public AiDecisionAdvice callAiProvider(...) {
  // AI call
}
```

**Acceptance:**
- retry logic respects error category
- retries bounded (no infinite loops)
- retry attempts logged in audit trail

#### 12.12.c — Error Budget

**Acceptable Failure Rate**

Define AI error budget:
- Target AI success rate: 95%
- Warning threshold: 90%
- Critical threshold: 85%

**Error budget tracking:**
- Track hourly AI success rate
- If below target: investigate
- If below warning: alert
- If below critical: consider disabling AI temporarily

**Budget reset:**
- Error budget measured over rolling 24h window
- Resets do not occur; continuous monitoring

**Acceptance:**
- error budget tracked continuously
- alerts configured at thresholds
- budget exhaustion triggers investigation

#### 12.12.d — Cascading Failure Prevention

**Circuit Breaker Pattern**

Protect system from cascading AI failures:

**States:**
- CLOSED: normal operation, AI calls proceed
- OPEN: AI failing, all calls fast-fail to deterministic fallback
- HALF_OPEN: test if AI recovered, limited calls allowed

**Transition logic:**
```
CLOSED → OPEN: 5 failures in 10 seconds
OPEN → HALF_OPEN: after 30 seconds
HALF_OPEN → CLOSED: 3 consecutive successes
HALF_OPEN → OPEN: any failure
```

**Acceptance:**
- circuit breaker implemented (Resilience4j)
- state transitions logged
- breaker state visible in metrics

---

## 12.13 — Observability & Operations

### Goal
Provide comprehensive visibility into AI subsystem health, performance, and quality for operational excellence.

#### 12.13.a — AI Observability Dashboard

**Real-Time Metrics**

Dashboard sections:

**1. Operational Health**
- AI request rate (req/min)
- Success rate (%)
- Error rate by category (%)
- Fallback rate (%)
- Circuit breaker state

**2. Performance**
- Latency: p50, p95, p99 (ms)
- Timeout rate (%)
- Cost per decision ($)
- Daily AI spend ($)

**3. Quality**
- Advice acceptance rate (%)
- Confidence distribution (histogram)
- Override rate (%)
- Validation rejection rate (%)

**4. RAG Performance**
- Retrieval latency (ms)
- Cache hit rate (%)
- Embedding generation rate (req/min)

**5. Provider Status**
- Per-provider success rate (%)
- Per-provider latency (ms)
- Model version in use

**Acceptance:**
- dashboard accessible to operations team
- real-time data (< 1 min lag)
- historical view (7 days)

#### 12.13.b — Alerting & SLOs

**Service Level Objectives**

Define SLOs for AI subsystem:

| Metric | SLO | Warning | Critical |
|--------|-----|---------|----------|
| Success Rate | ≥ 95% | < 90% | < 85% |
| Latency p95 | ≤ 2000ms | > 3000ms | > 5000ms |
| Advice Acceptance | ≥ 60% | < 50% | < 40% |
| Daily Cost | ≤ $100 | > $150 | > $200 |

**Alert routing:**
- WARNING → Slack channel
- CRITICAL → PagerDuty / on-call engineer

**Alert suppression:**
- No alert spam (max 1 alert per metric per hour)
- Alerts auto-resolve when metric returns to normal

**Acceptance:**
- SLOs defined and documented
- alerting configured
- alert routing tested

#### 12.13.c — Runbook for AI Incidents

**Incident Response Procedures**

**Scenario 1: AI provider outage**
1. Verify provider status page
2. Check circuit breaker state (should be OPEN)
3. Verify deterministic fallback working
4. Monitor success rate recovery
5. When provider recovers, breaker transitions to HALF_OPEN → CLOSED

**Scenario 2: AI quality degradation**
1. Check advice acceptance rate trend
2. Review recent AI responses in audit log
3. Check for model version change (provider-side update)
4. Run regression test suite
5. If model drift detected, consider rollback or model lock

**Scenario 3: Cost overrun**
1. Check cost per decision trend
2. Identify high-cost decisions (large context? complex reasoning?)
3. Verify cost controls enforced (max_cost_per_decision)
4. If necessary, temporarily disable AI or reduce traffic %

**Scenario 4: Latency spike**
1. Check provider latency vs internal latency
2. Check RAG retrieval latency
3. Check embedding cache hit rate
4. Verify timeout enforcement
5. If persistent, route traffic to faster model or increase timeout

**Acceptance:**
- runbook documented and accessible
- runbook reviewed quarterly
- incident scenarios tested in staging

#### 12.13.d — Operational Metrics Export

**Metrics Integration**

Export AI metrics to observability stack:
- Prometheus metrics endpoint
- Grafana dashboard templates
- CloudWatch/Datadog integration (if cloud-hosted)

**Key metrics exported:**
```
ai_requests_total{provider, model, status}
ai_request_duration_seconds{provider, model, quantile}
ai_advice_accepted_total{classification_type}
ai_cost_usd_total{provider, model}
ai_circuit_breaker_state{provider}
rag_retrieval_duration_seconds{quantile}
rag_cache_hit_rate
```

**Acceptance:**
- metrics exported in standard format
- integration with existing monitoring
- metrics retention aligned with ops policy

---

## 12.14 — Sub-Phase Dependency Graph

### Goal
Clarify implementation order and dependencies between sub-phases to enable efficient parallel work and avoid blocking.

#### Dependency Structure

```
12.1 (AI Classification Foundation) ← START HERE
  ├─ 12.1.a Classification Engine Abstraction (FOUNDATION)
  ├─ 12.1.b AI Adapter Layer
  ├─ 12.1.c Spring AI Integration
  ├─ 12.1.d Prompt Template Discipline
  ├─ 12.1.e Structured Output Contract
  └─ 12.1.f Deterministic Fallback

12.2 (AI Advisory Decision Layer) ← DEPENDS ON 12.1.b, 12.1.e
  ├─ 12.2.a Deterministic Decision First
  ├─ 12.2.b AI Advice Model
  ├─ 12.2.c Decision Validator
  └─ 12.2.d Final Decision Assembly

12.3 (RAG over Decision Memory) ← CAN START IN PARALLEL WITH 12.1/12.2
  ├─ 12.3.a Curated Decision Explanation Dataset
  ├─ 12.3.b Embedding Pipeline
  ├─ 12.3.c Embedding Cache
  ├─ 12.3.d Vector Storage (pgvector setup)
  ├─ 12.3.e Retrieval Service
  └─ 12.3.f Context Injection ← DEPENDS ON 12.2.b (AI Advice Model)

12.4 (Local Model Support) ← DEPENDS ON 12.1.b (AI Adapter Layer)
  ├─ 12.4.a Ollama Integration
  ├─ 12.4.b Mode Selection
  └─ 12.4.c Hybrid Provider Routing

12.5 (AI Safety & Governance) ← INTEGRATES WITH 12.1, 12.2, 12.3
  ├─ 12.5.a Prompt Versioning ← INTEGRATES WITH 12.1.d
  ├─ 12.5.b Input Sanitization ← INTEGRATES WITH 12.1.b
  ├─ 12.5.c Output Validation ← INTEGRATES WITH 12.2.c
  ├─ 12.5.d Cost Controls ← INTEGRATES WITH 12.1.b
  ├─ 12.5.e Latency Controls ← INTEGRATES WITH 12.1.b
  └─ 12.5.f AI Audit Trail ← INTEGRATES WITH 12.2.d

12.6 (Concurrency & Runtime Sanity) ← DEPENDS ON 12.1.b, 12.2
  ├─ 12.6.a Dedicated AI Executor
  ├─ 12.6.b Circuit Breaker
  └─ 12.6.c Graceful Degradation

12.7 (AI Quality Metrics) ← INTEGRATES WITH ALL SUB-PHASES
  ├─ 12.7.a RAG Quality Metrics ← DEPENDS ON 12.3.e
  ├─ 12.7.b AI Advice Quality Metrics ← DEPENDS ON 12.2.d
  ├─ 12.7.c Confidence Calibration ← DEPENDS ON 12.2.b
  ├─ 12.7.d Regression Testing ← DEPENDS ON 12.1.e, 12.3.e
  ├─ 12.7.e A/B Testing Framework ← DEPENDS ON 12.1.b
  ├─ 12.7.f Adversarial Testing ← DEPENDS ON 12.5.b
  └─ 12.7.g Production Quality Monitoring ← DEPENDS ON ALL

12.8 (Prompt Engineering Lifecycle) ← INTEGRATES WITH 12.1.d, 12.5.a
  ├─ 12.8.a Prompt Template Repository
  ├─ 12.8.b Prompt Approval Process
  ├─ 12.8.c Prompt A/B Testing ← DEPENDS ON 12.7.e
  ├─ 12.8.d Prompt Rollback Strategy
  └─ 12.8.e Prompt Security Review

12.9 (Model Versioning & Lifecycle) ← INTEGRATES WITH 12.1.c, 12.5.f
  ├─ 12.9.a Model Version Tracking
  ├─ 12.9.b Model Drift Detection ← DEPENDS ON 12.7.d
  ├─ 12.9.c Model Migration Strategy ← DEPENDS ON 12.7.e
  ├─ 12.9.d Model Deprecation Handling
  └─ 12.9.e Multi-Model Strategy ← DEPENDS ON 12.4.c

12.10 (RAG Dataset Curation) ← DEPENDS ON 12.3.a
  ├─ 12.10.a Curation Criteria
  ├─ 12.10.b Curation Process
  ├─ 12.10.c Dataset Quality Control ← DEPENDS ON 12.7.a
  ├─ 12.10.d Dataset Growth Management
  └─ 12.10.e Dataset Refresh & Versioning

12.11 (Privacy & Compliance) ← INTEGRATES WITH 12.1.b, 12.5
  ├─ 12.11.a PII Handling in Prompts
  ├─ 12.11.b Data Residency Requirements ← INTEGRATES WITH 12.4.b
  ├─ 12.11.c GDPR Compliance
  ├─ 12.11.d AI Provider Data Policies
  └─ 12.11.e Audit Log Retention ← INTEGRATES WITH 12.5.f

12.12 (Error Taxonomy & Retry) ← DEPENDS ON 12.1.b, 12.6.b
  ├─ 12.12.a Error Classification
  ├─ 12.12.b Retry Strategy
  ├─ 12.12.c Error Budget ← INTEGRATES WITH 12.7.g
  └─ 12.12.d Cascading Failure Prevention

12.13 (Observability & Operations) ← INTEGRATES WITH ALL
  ├─ 12.13.a AI Observability Dashboard ← DEPENDS ON 12.7.g
  ├─ 12.13.b Alerting & SLOs
  ├─ 12.13.c Runbook for AI Incidents
  └─ 12.13.d Operational Metrics Export

12.14 (Sub-Phase Dependencies) ← META (this section)
```

#### Critical Path

**Minimum viable AI integration path:**
1. 12.1.a → 12.1.b → 12.1.c → 12.1.e → 12.1.f (Classification foundation)
2. 12.2.a → 12.2.b → 12.2.c → 12.2.d (Advisory layer)
3. 12.5.f (AI Audit Trail - minimum governance)
4. 12.6.a → 12.6.b (Execution safety)

**This gives you:** Working AI advisory system with safety basics.

**To add RAG:**
- 12.3.a → 12.3.b → 12.3.c → 12.3.d → 12.3.e → 12.3.f

**To add full governance:**
- 12.5.a, 12.5.b, 12.5.c, 12.5.d, 12.5.e
- 12.8 (Prompt lifecycle)
- 12.11 (Privacy)

**To add production-grade quality:**
- 12.7 (Quality metrics)
- 12.13 (Observability)

#### Parallel Work Opportunities

**Can work in parallel:**
- Team A: 12.1 + 12.2 (core AI integration)
- Team B: 12.3 (RAG infrastructure)
- Team C: 12.5 + 12.11 (governance & compliance)

**Must be sequential:**
- 12.1.b must complete before 12.2
- 12.2.b must complete before 12.3.f
- 12.7 can only start after respective dependencies complete

**Acceptance:**
- dependency graph documented
- blocks identified before sprint planning
- parallel work opportunities exploited

---

## Verification

### 12.V1 — AI Classification Verification

**Validate:**
- AI output parses correctly
- Schema enforcement works
- Deterministic fallback triggers correctly

**Test scenarios:**
1. **Happy path:**
    - Send valid event
    - AI classifies correctly
    - Schema valid
    - Confidence >= 0.85
    - Result: AI classification accepted

2. **Malformed response:**
    - Mock AI returns invalid JSON
    - Result: schema validation fails, fallback to deterministic

3. **Low confidence:**
    - Mock AI returns confidence 0.65
    - Result: validator rejects, fallback to deterministic

4. **Timeout:**
    - Mock AI delays response >2s
    - Result: timeout triggers, fallback to deterministic

5. **Provider unavailable:**
    - Disconnect provider
    - Result: connection error, fallback to deterministic

**Acceptance:**
- All 5 scenarios pass
- Fallback path logs clear reason
- Audit trail records fallback event

### 12.V2 — Advisory Override Verification

**Validate:**
- Deterministic decision produced first
- AI advice may propose change
- Validator may accept or reject
- Final decision lineage is explicit

**Test scenarios:**
1. **AI suggests same classification:**
    - Deterministic: DEVICE_ERROR
    - AI suggests: DEVICE_ERROR, confidence 0.91
    - Result: AI reinforces deterministic decision

2. **AI suggests different classification (valid):**
    - Deterministic: NETWORK_ERROR
    - AI suggests: DEVICE_ERROR, confidence 0.88
    - Validator checks: DEVICE_ERROR is valid route
    - Result: AI advice accepted, final decision = DEVICE_ERROR

3. **AI suggests different classification (invalid):**
    - Deterministic: DEVICE_ERROR
    - AI suggests: UNKNOWN_TYPE, confidence 0.92
    - Validator checks: UNKNOWN_TYPE not in allowed enum
    - Result: AI advice rejected, final decision = DEVICE_ERROR

4. **AI suggests override (low confidence):**
    - Deterministic: NETWORK_ERROR
    - AI suggests: DEVICE_ERROR, confidence 0.72
    - Validator checks: confidence < 0.85 threshold
    - Result: AI advice rejected due to low confidence

5. **AI advice with historical context:**
    - RAG retrieves 3 similar decisions
    - AI reasoning references historical patterns
    - Confidence boosted to 0.89
    - Result: AI advice accepted, reasoning includes RAG context

**Acceptance:**
- All 5 scenarios pass
- Audit trail shows deterministic decision, AI advice, validator decision
- Final decision clearly labeled with influence source

### 12.V3 — RAG Quality Verification

**Validate:**
- Similar decisions retrieved from curated explanation dataset
- Retrieval improves prompt context
- Raw event noise is excluded

**Test scenarios:**
1. **Successful retrieval:**
    - Query: "Device telemetry spike detected"
    - Retrieved: 3 decisions with similar patterns
    - Quality scores: all >= 0.75
    - Result: relevant context injected into prompt

2. **No similar decisions:**
    - Query: completely novel event pattern
    - Retrieved: 0 decisions (similarity threshold not met)
    - Result: AI advisory proceeds without historical context

3. **Cache hit:**
    - Same query executed twice
    - First: embedding generated
    - Second: embedding reused from cache
    - Result: second query 10x faster, no embedding cost

4. **Quality filtering:**
    - 10 potential matches found
    - 3 have quality_score >= 0.75
    - 7 have quality_score < 0.75
    - Result: only top 3 high-quality decisions retrieved

5. **Policy version filtering:**
    - Current policy: v2.3
    - Retrieved decisions: all from v2.2 or v2.3
    - Old policy v1.x decisions excluded
    - Result: context relevant to current policy logic

**Acceptance:**
- All 5 scenarios pass
- Retrieval latency <100ms p95
- Embedding cache hit rate >80% after warm-up
- Retrieved decisions are curated, not raw events

### 12.V4 — Embedding Cache Verification

**Validate:**
- Identical content reuses cached embeddings
- Duplicate embedding generation avoided
- Cache hit/miss metrics visible

**Test scenarios:**
1. **Cache miss → generate:**
    - New decision explanation
    - Hash: ABC123 (not in cache)
    - Action: generate embedding
    - Result: embedding stored with hash ABC123

2. **Cache hit → reuse:**
    - Same decision explanation
    - Hash: ABC123 (found in cache)
    - Action: retrieve cached embedding
    - Result: no API call, instant retrieval

3. **Similar but not identical:**
    - Decision explanation differs by 1 character
    - Hash: ABC124 (different hash)
    - Action: generate new embedding
    - Result: cache miss, new embedding generated

4. **Cache eviction (LRU):**
    - Cache at max capacity
    - New embedding needed
    - Least recently used entry: last accessed 120 days ago
    - Action: evict old entry, store new one
    - Result: cache size maintained

5. **Model change:**
    - Cached embedding: model A, dimension 1536
    - New embedding: model B, dimension 768
    - Hash: same content, different model
    - Action: generate new embedding for model B
    - Result: both embeddings cached (different models)

**Acceptance:**
- All 5 scenarios pass
- Cache hit rate metric visible in dashboard
- Cost savings measurable (embedding API calls reduced)

### 12.V5 — Cost and Latency Verification

**Validate:**
- Every AI call records cost and latency
- Budget overflow triggers fallback
- Timeout triggers fallback

**Test scenarios:**
1. **Normal operation:**
    - AI call completes in 500ms
    - Cost: $0.002
    - Daily budget: $5.00 remaining
    - Result: call succeeds, metrics recorded

2. **Cost budget daily limit:**
    - Daily spend: $99.98
    - Daily budget: $100.00
    - New call estimated cost: $0.05
    - Action: reject AI call, use fallback
    - Result: deterministic decision, budget protected

3. **Cost budget per-decision limit:**
    - Decision cost reaches $0.06
    - Per-decision limit: $0.05
    - Action: reject AI call, use fallback
    - Result: budget respected

4. **Timeout:**
    - AI provider responds in 3.5s
    - Timeout configured: 2s
    - Action: cancel request, use fallback
    - Result: latency SLA maintained

5. **Latency spike (circuit breaker):**
    - 5 consecutive calls >3s
    - Circuit breaker opens
    - Next 10 calls fast-fail
    - After 30s: circuit breaker half-open, retry
    - Result: system protected from cascading latency

**Acceptance:**
- All 5 scenarios pass
- Cost tracking accurate to $0.0001
- Latency metrics visible (p50, p95, p99)
- Budget enforcement tested

### 12.V6 — Local Runtime Verification

**Validate:**
- Ollama path works
- Hybrid mode routes correctly
- Local fallback behaves as expected

**Test scenarios:**
1. **Local-only mode:**
    - Configuration: mode = local
    - Provider: Ollama
    - Model: llama3:8b
    - Result: all AI calls go to local model

2. **Cloud-only mode:**
    - Configuration: mode = cloud
    - Provider: OpenAI
    - Model: gpt-4o-mini
    - Result: all AI calls go to cloud model

3. **Hybrid mode (privacy-based routing):**
    - Event with privacy_level = HIGH
    - Routing policy: HIGH → local
    - Result: call routed to Ollama

4. **Hybrid mode (latency-based routing):**
    - Event with latency_requirement <100ms
    - Routing policy: <100ms → local
    - Result: call routed to Ollama (faster)

5. **Local fallback on cloud failure:**
    - Configuration: mode = hybrid
    - Primary: cloud (OpenAI)
    - Cloud provider times out
    - Action: fallback to local (Ollama)
    - Result: decision completes using local model

**Acceptance:**
- All 5 scenarios pass
- Provider selection logged in audit trail
- Mode switch requires config change only, no code change

### 12.V7 — Governance Verification

**Validate:**
- Prompt version persisted
- Prompt hash persisted
- AI audit record created
- Accepted/rejected AI advice reconstructable

**Test scenarios:**
1. **Full audit trail:**
    - Decision made with AI advice
    - Audit record contains:
        - decision_id
        - provider: openai
        - model: gpt-4o-mini
        - model_version: 2024-11-20
        - prompt_version: 1.1.0
        - prompt_hash: sha256(...)
        - confidence: 0.87
        - accepted_by_validator: true
    - Result: complete lineage traceable

2. **Rejected advice:**
    - AI suggests UNKNOWN_TYPE (invalid)
    - Validator rejects
    - Audit record contains:
        - accepted_by_validator: false
        - rejection_reason: "Invalid classification type"
    - Result: rejection reason explicit

3. **Prompt version change:**
    - Upgrade from prompt v1.0.0 to v1.1.0
    - A/B test runs for 7 days
    - Audit trail shows mix of both versions
    - Query: "show F1 score by prompt version"
    - Result: version comparison possible

4. **Compliance report generation:**
    - Query last 30 days of AI audit trail
    - Generate report:
        - Total calls: 45,234
        - Acceptance rate: 71%
        - Cost: $1,247.82
        - Models used: gpt-4o-mini (98%), llama3 (2%)
        - PII incidents: 0
    - Result: compliance-ready report

5. **Decision reconstruction:**
    - Given decision_id = 12345
    - Retrieve from audit trail:
        - Deterministic decision
        - AI advice
        - Validator decision
        - Final decision
        - Prompt used
        - Model used
    - Result: full decision path reconstructed

**Acceptance:**
- All 5 scenarios pass
- Audit trail queryable for any decision
- Compliance report generatable
- **External auditor can verify AI usage**

### 12.V8 — Quality Metrics Verification

**Validate:**
- Metrics collected automatically
- Regression detection works
- Calibration system functional
- A/B testing framework operational

**Test scenarios:**
1. **Metrics collection:**
    - Process 1000 decisions
    - Metrics auto-recorded:
        - F1 score
        - Acceptance rate
        - Latency percentiles
        - Cost per decision
    - Result: metrics queryable in dashboard

2. **Regression detection (F1 drop):**
    - Baseline F1: 0.89 (7-day avg)
    - Current F1: 0.82 (24h avg)
    - Drop: 7.9% (>5% threshold)
    - Action: alert triggered
    - Result: "AI quality regression detected" alert sent

3. **Confidence calibration:**
    - Collect 500 decisions with confidence 0.80-0.90
    - Actual accuracy: 78% (not 85%)
    - Calibration: AI overconfident in this range
    - Action: raise threshold from 0.85 to 0.88
    - Result: acceptance threshold adjusted

4. **A/B test (prompt comparison):**
    - Variant A: prompt v1.0.0 (50% traffic)
    - Variant B: prompt v1.1.0 (50% traffic)
    - Run 7 days, 1000 decisions each
    - Results:
        - Variant A F1: 0.87
        - Variant B F1: 0.91
    - Statistical test: p-value = 0.003
    - Decision: promote v1.1.0
    - Result: v1.1.0 becomes production prompt

5. **Dataset health monitoring:**
    - Check dataset coverage:
        - DEVICE_ERROR: 2,341 examples ✓
        - NETWORK_ERROR: 1,876 examples ✓
        - USER_ERROR: 489 examples ✗ (target: 1000)
    - Alert: "USER_ERROR under-represented in dataset"
    - Result: dataset gap identified

**Acceptance:**
- All 5 scenarios pass
- Metrics accurate and timely
- Regression alerts actionable
- A/B tests statistically valid
- Dataset health visible

### 12.V9 — Observability Verification

**Validate:**
- Dashboards functional
- Health endpoints accurate
- Alerts trigger correctly
- Distributed tracing complete

**Test scenarios:**
1. **Dashboard refresh:**
    - Access Grafana dashboard
    - Real-time metrics update <30s
    - Historical data queryable (30 days)
    - Result: ops team can monitor AI subsystem

2. **Health endpoint (healthy state):**
    - Call /actuator/health/ai
    - Response: status = UP
    - All components UP
    - Circuit breaker CLOSED
    - Result: health check passes

3. **Health endpoint (degraded state):**
    - Simulate circuit breaker open
    - Call /actuator/health/ai
    - Response: status = DOWN
    - Details: circuitBreaker = OPEN
    - Result: health check correctly reflects degradation

4. **Critical alert:**
    - Trigger cost budget exceeded
    - Alert sent to PagerDuty
    - On-call engineer paged
    - Result: critical issue escalated

5. **Distributed trace:**
    - Process decision with AI advisory
    - View trace in Jaeger
    - Spans visible:
        - DeterministicClassifier: 50ms
        - RAG Retrieval: 120ms
        - Provider API Call: 780ms
        - DecisionValidator: 30ms
    - Total: 980ms
    - Result: latency breakdown visible

**Acceptance:**
- All 5 scenarios pass
- Dashboards accessible to ops team
- Health checks integrate with monitoring
- Alerts routed correctly
- Traces provide debugging value

### 12.V10 — Privacy & Compliance Verification

**Validate:**
- PII handling enforced
- Data residency respected
- GDPR rights implementable
- Compliance audit possible

**Test scenarios:**
1. **PII detection:**
    - Event contains email: user@example.com
    - Sanitization detects PII
    - Email replaced with [EMAIL]
    - Result: no PII in AI prompt

2. **Privacy-level routing:**
    - Event marked privacy_level = HIGH
    - Routing policy: HIGH → local
    - AI call routed to Ollama (local)
    - Cloud providers not used
    - Result: privacy policy enforced

3. **Data residency (EU):**
    - Customer region: EU
    - Provider: Anthropic EU endpoint
    - Audit trail logs: region = eu-west-1
    - Result: data stays in EU

4. **Right to erasure (GDPR):**
    - User requests deletion
    - System purges:
        - AI audit trail for user's decisions
        - Embeddings containing user data
        - Decision explanations
    - Result: user data erased

5. **Compliance audit report:**
    - Generate report for external auditor
    - Contents:
        - Total AI calls: 50,000
        - PII incidents: 0
        - Data residency compliance: 100%
        - Cost: $2,341.22
        - Models used: documented
    - Result: audit-ready report

**Acceptance:**
- All 5 scenarios pass
- PII never reaches cloud providers
- Data residency configurable and enforced
- GDPR rights implementable
- **Compliance report suitable for external audit**

---

## Done Criteria

Phase 12 is complete when:

### Core Implementation
- ✅ Classification engine abstraction implemented
- ✅ AI adapter layer implemented
- ✅ Deterministic-first advisory model implemented
- ✅ AI-first classification remains future-ready without refactor
- ✅ RAG uses curated decision explanation dataset
- ✅ Embedding cache implemented
- ✅ Prompt versioning implemented
- ✅ AI safety gates enforced
- ✅ AI audit trail persisted
- ✅ Local/cloud/hybrid runtime supported
- ✅ Deterministic fallback validated

### Quality & Governance (NEW)
- ✅ AI quality metrics defined and collected
- ✅ Confidence calibration system operational
- ✅ A/B testing framework functional
- ✅ Regression detection automated
- ✅ Dataset health monitoring active
- ✅ Prompt governance workflow enforced
- ✅ Model lifecycle management implemented
- ✅ Error taxonomy and retry logic implemented

### Observability (NEW)
- ✅ AI dashboards deployed and accessible
- ✅ Health endpoints integrated
- ✅ Alerting rules configured
- ✅ Distributed tracing operational
- ✅ Audit query interface functional

### Privacy & Compliance (NEW)
- ✅ PII detection and anonymization enforced
- ✅ Privacy-level routing implemented
- ✅ Data residency configurable
- ✅ GDPR rights implementable
- ✅ Compliance audit report generatable

### Testing
- ✅ All 10 verification scenarios pass
- ✅ All integration tests green
- ✅ CI green

---

## Architectural Impact

Phase 12 transforms TriageMate from:

**deterministic decision engine**

into:

**deterministic decision engine**  
**+ AI advisory layer**  
**+ semantic decision memory**  
**+ governed AI auditability**  
**+ measurable AI quality**  
**+ privacy-first AI integration**

**Capabilities gained:**
- AI-assisted classification with deterministic fallback
- Advisory override proposals with validator governance
- Historical semantic reasoning via RAG
- Explainable decision support with audit trail
- Provider flexibility (cloud/local/hybrid)
- Governance-grade AI traceability
- **Data-driven AI quality measurement**
- **Confidence calibration for trustworthy predictions**
- **Privacy-first prompt assembly**
- **Compliance-ready audit reporting**
- **Observable AI subsystem with dashboards and alerts**
- **Regression detection and automatic quality monitoring**

The system becomes smarter without sacrificing determinism, replayability, privacy, or control.

---

## Version Target

**Release Tag:** `v0.12.0`