# PHASE 12A — AI Advisory Core (MVP)

## State Marker

```yaml
Change ID:    TM-12A
Branch:       feat/phase-12a-ai-advisory-core
Stage:        A (Design)
Owner:        Gabriele
Depends On:   v0.11.x
Target Tag:   v0.12.0

AI Integration Status:
  ai_adapter_interface:       not_implemented
  spring_ai_provider:         not_implemented
  structured_output:          not_implemented
  deterministic_fallback:     not_implemented
  ai_audit_trail:             not_implemented
  circuit_breaker:            not_implemented
  cost_tracking:              not_implemented
  prompt_template:            not_implemented

Tests:
  unit:        pending
  integration: pending
  manual:      pending

Completion Criteria: NOT_MET
```

## Objective

Introduce AI into TriageMate as **decision support, not blind automation**. Phase 12A is the minimum viable AI integration: one provider, one prompt, deterministic fallback, full auditability.

The system evolves from a deterministic decision engine into a **deterministic engine with an AI advisory layer**. The deterministic system remains authoritative; AI provides classification support and reasoning enrichment.

**Scope boundary:** Phase 12A delivers a working AI advisory pipeline. RAG, multi-provider, local models, governance workflows, and quality measurement systems are deferred to Phases 12B and 12C.

---

## Design Freeze

The following architectural decisions are **fixed** for Phase 12.

### Decision 1 — AI position in the pipeline

AI is placed **after deterministic decisioning**, as an advisor.

```
Input Event
   |
Deterministic Classifier / Policy
   |
AI Decision Advisor
   |
Decision Validator
   |
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

This means classification must already be **abstracted behind a stable interface** — which is already the case with the existing `Policy` interface.

### Decision 3 — Model and prompt lifecycle are versioned

Both AI models and prompt templates are versioned assets. In 12A, versioning is simple (resource files + hash tracking). Full governance workflows are deferred to Phase 12C.

### Decision 4 — Privacy-first prompt assembly

No PII or sensitive data enters AI prompts without explicit sanitization. Phase 12A implements basic PII scrubbing. Full GDPR compliance and data residency routing are deferred to V2.

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

---

## Integration with Existing Codebase

Phase 12A builds on top of the existing decision pipeline — it does NOT replace it.

### Current architecture (Phase 7-11)

```
InputReceivedConsumer.onMessage()
  |
InputReceivedProcessor.process()
  |  DecisionContextFactory.fromEnvelope()
  |  DecisionService.decide()
  |    +-- List<Policy> policies
  |    +-- CostGuard costGuard
  |
DefaultDecisionRouter.route()
  |  DecisionOutcomePublisher.publish() --> Outbox --> Kafka
```

### Key existing interfaces

```java
// Already exists — the decision orchestrator
public interface DecisionService {
    DecisionResult decide(DecisionContext<?> context);
}

// Already exists — pluggable policy evaluation
public interface Policy {
    PolicyResult evaluate(DecisionContext<?> context);
}

// Already exists — cost control
public interface CostGuard {
    CostDecision evaluateCost(DecisionContext<?> context);
}
```

### Phase 12A extension point

The AI advisory layer wraps around `DecisionService` as a **decorator**:

```java
public class AiAdvisedDecisionService implements DecisionService {

    private final DecisionService delegate;          // existing DefaultDecisionService
    private final AiDecisionAdvisor aiAdvisor;       // new
    private final AiAdviceValidator adviceValidator;  // new
    private final AiAuditService auditService;       // new

    @Override
    public DecisionResult decide(DecisionContext<?> context) {
        // Step 1: Deterministic decision (existing pipeline)
        DecisionResult deterministicResult = delegate.decide(context);

        // Step 2: AI advisory (optional, bounded)
        AiDecisionAdvice advice = aiAdvisor.advise(context, deterministicResult);

        // Step 3: Validate AI advice against policy
        ValidatedAdvice validated = adviceValidator.validate(deterministicResult, advice);

        // Step 4: Audit
        auditService.record(context, deterministicResult, advice, validated);

        // Step 5: Assemble final result
        return assembleResult(deterministicResult, validated);
    }
}
```

**This approach:**
- Preserves the existing `DefaultDecisionService` unchanged
- Uses the Decorator pattern — clean, testable, reversible
- AI is injectable via Spring config (`@ConditionalOnProperty("triagemate.ai.enabled")`)
- When AI is disabled, `delegate.decide()` runs alone (zero overhead)

---

## Sub-Phases

## 12A.1 — AI Adapter Layer

### Goal
Introduce a provider-neutral AI adapter that decouples business logic from AI provider specifics.

#### 12A.1.a — AiDecisionAdvisor Interface

```java
public interface AiDecisionAdvisor {
    AiDecisionAdvice advise(DecisionContext<?> context, DecisionResult deterministicResult);
}
```

**Responsibilities:**
- prompt building from context + deterministic result
- provider invocation
- response parsing into structured advice
- latency tracking
- cost tracking
- timeout enforcement

**Fallback behavior:**
If AI fails for any reason, `advise()` returns `AiDecisionAdvice.NONE` (no advice). The deterministic result stands unchanged.

#### 12A.1.b — AI Advice Model

Structured advisory object:

```java
public record AiDecisionAdvice(
    String suggestedClassification,
    double confidence,
    String reasoning,
    boolean recommendsOverride,
    String provider,
    String model,
    String modelVersion,
    String promptVersion,
    String promptHash,
    int inputTokens,
    int outputTokens,
    double costUsd,
    long latencyMs
) {
    public static final AiDecisionAdvice NONE = new AiDecisionAdvice(
        null, 0.0, "AI advisory not available", false,
        null, null, null, null, null, 0, 0, 0.0, 0
    );

    public boolean isPresent() {
        return this != NONE;
    }
}
```

**Acceptance:**
- AI advice is separate from final decision
- advice is explicit, typed, and serializable
- no implicit override behavior
- `NONE` sentinel for fallback/skip cases

#### 12A.1.c — Spring AI Integration (Single Provider)

Integrate Spring AI with **one** provider for V1. Recommended: Anthropic Claude.

**Dependency:**
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-anthropic-spring-boot-starter</artifactId>
</dependency>
```

**Configuration:**
```yaml
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:}
      chat:
        options:
          model: claude-sonnet-4-20250514
          max-tokens: 500
          temperature: 0.0

triagemate:
  ai:
    enabled: ${AI_ENABLED:false}
    provider: anthropic
    timeouts:
      advisory: 5s
```

**Note:** Pin the Spring AI version explicitly in pom.xml. Spring AI is evolving rapidly — do not rely on BOM auto-resolution.

**Acceptance:**
- provider config externalized
- no secret in code
- AI can be disabled via `triagemate.ai.enabled=false`
- model version explicitly tracked

#### 12A.1.d — Prompt Template

One versioned prompt template stored as a classpath resource.

**Location:** `src/main/resources/prompts/advisory/v1.0.0-decision-advisor.txt`

```
You are a decision advisory system for TriageMate.

CURRENT DETERMINISTIC DECISION:
Classification: {{classification}}
Outcome: {{outcome}}
Reason: {{reason}}

EVENT CONTEXT:
Event Type: {{eventType}}
Payload Summary: {{payloadSummary}}

YOUR TASK:
Analyze the deterministic decision and provide advisory input.
Return your response as JSON only, with this exact schema:

{
  "suggestedClassification": "string (one of: {{allowedClassifications}})",
  "confidence": number (0.0 to 1.0),
  "reasoning": "string (max 200 chars)",
  "recommendsOverride": boolean
}

RULES:
- Do not invent classifications outside the allowed list
- If you agree with the deterministic decision, set recommendsOverride to false
- Only recommend override if confidence >= 0.85
- Be concise in reasoning
```

**Prompt loading:**
```java
@Component
public class PromptTemplateService {
    private final String template;
    private final String promptHash;
    private final String promptVersion = "1.0.0";

    public PromptTemplateService() {
        this.template = loadResource("prompts/advisory/v1.0.0-decision-advisor.txt");
        this.promptHash = sha256(this.template);
    }

    public String render(Map<String, String> variables) { ... }
    public String getPromptVersion() { return promptVersion; }
    public String getPromptHash() { return promptHash; }
}
```

**Acceptance:**
- prompt stored as versioned classpath resource
- prompt hash computed at startup (immutable)
- prompt version and hash available for audit trail

#### 12A.1.e — Structured Output Contract

All AI responses must follow a strict JSON schema.

**Validation rules:**
- mandatory fields present (`suggestedClassification`, `confidence`, `reasoning`, `recommendsOverride`)
- `suggestedClassification` must be in allowed enum values
- `confidence` range enforced [0.0, 1.0]
- malformed JSON rejected
- extra fields ignored

**Response parsing:**
```java
public class AiResponseParser {
    private final ObjectMapper objectMapper;

    public AiClassificationResponse parse(String rawResponse) {
        try {
            AiClassificationResponse response = objectMapper.readValue(
                extractJson(rawResponse), AiClassificationResponse.class);
            validate(response);
            return response;
        } catch (Exception e) {
            throw new AiResponseParseException("Invalid AI response", e);
        }
    }
}
```

**Acceptance:**
- no free-form response parsing
- schema failures trigger fallback to `AiDecisionAdvice.NONE`
- response parsing covered by unit tests

---

## 12A.2 — Decision Validator

### Goal
Deterministic validation of AI advice before it can influence the final decision.

#### 12A.2.a — Validator Logic

```java
@Component
public class AiAdviceValidator {

    private static final double MIN_CONFIDENCE_FOR_OVERRIDE = 0.85;
    private static final double MIN_CONFIDENCE_FOR_SUGGESTION = 0.70;

    public ValidatedAdvice validate(DecisionResult deterministic, AiDecisionAdvice advice) {
        if (!advice.isPresent()) {
            return ValidatedAdvice.noAdvice();
        }

        if (advice.confidence() < MIN_CONFIDENCE_FOR_SUGGESTION) {
            return ValidatedAdvice.rejected(advice, "Confidence too low: " + advice.confidence());
        }

        if (!isAllowedClassification(advice.suggestedClassification())) {
            return ValidatedAdvice.rejected(advice, "Invalid classification: " + advice.suggestedClassification());
        }

        if (advice.recommendsOverride() && advice.confidence() >= MIN_CONFIDENCE_FOR_OVERRIDE) {
            return ValidatedAdvice.accepted(advice);
        }

        return ValidatedAdvice.advisory(advice); // logged but not applied
    }
}
```

**Possible outcomes:**
- `ACCEPTED` — advice applied (confidence >= 0.85, valid classification, recommends override)
- `ADVISORY` — logged but not applied (confidence >= 0.70, informational)
- `REJECTED` — advice rejected (low confidence, invalid classification)
- `NO_ADVICE` — AI was unavailable or returned NONE

**Acceptance:**
- validator is deterministic
- AI cannot bypass policy
- final decision remains governed
- confidence thresholds configurable

#### 12A.2.b — Final Decision Assembly

Combine deterministic decision + validated advice into final `DecisionResult`.

**New fields in DecisionResult (or in a wrapper):**
- `aiAdvicePresent` (boolean)
- `aiAdviceAccepted` (boolean)
- `aiAdviceRejectedReason` (String, nullable)
- `aiConfidence` (double)
- `aiModelVersion` (String)
- `aiPromptVersion` (String)

**Acceptance:**
- final decision lineage is explicit
- audit clearly shows AI influence or rejection
- no hidden mutation of decision result

---

## 12A.3 — Deterministic Fallback & Error Handling

### Goal
Ensure the system ALWAYS produces a decision, regardless of AI state.

#### 12A.3.a — Error Taxonomy

**Transient errors** (retry up to 2 times with exponential backoff):
- network timeout
- HTTP 429 (rate limit)
- HTTP 503 (service unavailable)

**Permanent errors** (no retry, immediate fallback):
- HTTP 401 (authentication failure)
- HTTP 400 (malformed request)
- malformed response schema
- confidence below threshold

**Budget errors** (no retry, immediate fallback):
- cost budget exceeded
- latency timeout exceeded
- circuit breaker open

#### 12A.3.b — Retry Logic

```java
RetryConfig retryConfig = RetryConfig.custom()
    .maxAttempts(3)
    .waitDuration(Duration.ofMillis(500))
    .retryOnException(e -> e instanceof TransientAiException)
    .ignoreExceptions(PermanentAiException.class, BudgetExceededException.class)
    .build();
```

#### 12A.3.c — Circuit Breaker

```yaml
resilience4j:
  circuitbreaker:
    instances:
      aiProvider:
        failure-rate-threshold: 50
        slow-call-rate-threshold: 50
        slow-call-duration-threshold: 3s
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        sliding-window-size: 10
```

**State exposed in health endpoint:**
```
/actuator/health/ai → { "status": "UP", "circuitBreaker": "CLOSED" }
```

#### 12A.3.d — Dedicated AI Executor

AI calls MUST NOT run on Kafka consumer threads.

```java
@Configuration
public class AiExecutorConfig {
    @Bean("aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ai-advisor-");
        // IMPORTANT: AbortPolicy, NOT CallerRunsPolicy
        // CallerRunsPolicy would block the Kafka consumer thread
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setTaskDecorator(new MdcTaskDecorator()); // MDC propagation from Phase 11
        executor.initialize();
        return executor;
    }
}
```

When queue is full: catch `RejectedExecutionException` → return `AiDecisionAdvice.NONE`.

**Acceptance:**
- consumer threads never blocked by AI latency
- queue overflow → graceful fallback, not Kafka rebalancing
- MDC context propagated to AI threads

---

## 12A.4 — AI Audit Trail

### Goal
Make every AI interaction fully traceable and reconstructable.

#### 12A.4.a — Audit Table

**Flyway migration:**
```sql
CREATE TABLE ai_decision_audit (
    id              BIGSERIAL PRIMARY KEY,
    decision_id     VARCHAR(255) NOT NULL,
    event_id        VARCHAR(255) NOT NULL,
    provider        VARCHAR(50),
    model           VARCHAR(100),
    model_version   VARCHAR(100),
    prompt_version  VARCHAR(50),
    prompt_hash     VARCHAR(64),
    confidence      DOUBLE PRECISION,
    suggested_classification VARCHAR(100),
    recommends_override BOOLEAN,
    reasoning       TEXT,
    accepted_by_validator BOOLEAN,
    rejection_reason VARCHAR(500),
    input_tokens    INTEGER,
    output_tokens   INTEGER,
    cost_usd        DOUBLE PRECISION,
    latency_ms      BIGINT,
    error_type      VARCHAR(50),
    error_message   TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_audit_decision_id ON ai_decision_audit(decision_id);
CREATE INDEX idx_ai_audit_created_at ON ai_decision_audit(created_at);
```

#### 12A.4.b — Audit Service

```java
@Component
public class AiAuditService {
    private final JdbcTemplate jdbcTemplate;

    public void record(
        DecisionContext<?> context,
        DecisionResult deterministicResult,
        AiDecisionAdvice advice,
        ValidatedAdvice validated
    ) {
        // Persist all fields including error cases
    }
}
```

**Retention:** 90 days for detailed records. Archival strategy deferred to Phase 12C.

**Acceptance:**
- every AI call recorded (success, failure, skip)
- AI influence on decision reconstructable from audit trail
- error cases logged with error_type and error_message

---

## 12A.5 — Cost Tracking

### Goal
Track and enforce AI cost limits.

#### 12A.5.a — Per-Decision Cost Tracking

Track on every AI call:
- `input_tokens`
- `output_tokens`
- `cost_usd` (computed from provider pricing)

#### 12A.5.b — Cost Limits

```yaml
triagemate:
  ai:
    cost:
      max-per-decision-usd: 0.05
      max-daily-usd: 100.00
```

When limit exceeded: return `AiDecisionAdvice.NONE`, log warning, increment `triagemate_ai_budget_exceeded_total` counter.

#### 12A.5.c — AI Metrics (Prometheus)

```
triagemate_ai_calls_total{provider, status}
triagemate_ai_latency_seconds{provider, quantile}
triagemate_ai_cost_usd_total{provider}
triagemate_ai_advice_accepted_total
triagemate_ai_advice_rejected_total
triagemate_ai_fallback_total{reason}
triagemate_ai_circuit_breaker_state{provider}
```

**Acceptance:**
- cost tracked on every AI call
- budget breaches trigger fallback
- metrics exposed on `/actuator/prometheus`

---

## 12A.6 — Input Sanitization

### Goal
Prevent PII leakage and prompt injection.

#### 12A.6.a — Prompt Sanitizer

```java
@Component
public class PromptSanitizer {

    private static final int MAX_INPUT_LENGTH = 2000;

    public String sanitize(String input) {
        if (input == null) return "";
        String cleaned = input
            // Prompt injection patterns
            .replaceAll("(?i)(ignore|disregard|forget).{0,20}(previous|above|instructions)", "[FILTERED]")
            .replaceAll("(?i)(system:|assistant:|user:)", "[FILTERED]")
            // Basic PII patterns
            .replaceAll("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b", "[EMAIL]")
            .replaceAll("\\b\\d{3}-\\d{2}-\\d{4}\\b", "[SSN]")
            .replaceAll("\\b\\d{16}\\b", "[CARD]");
        return cleaned.substring(0, Math.min(cleaned.length(), MAX_INPUT_LENGTH));
    }
}
```

**Acceptance:**
- sanitization applied before every prompt assembly
- no raw user text injected into prompts
- PII patterns detected and replaced

---

## Verification

### 12A.V1 — AI Advisory Happy Path

1. Send valid event
2. Deterministic decision produced (ACCEPT)
3. AI advisor called, returns classification with confidence 0.90
4. Validator accepts advice
5. Final decision includes AI metadata
6. Audit trail records full interaction
7. Prometheus metrics updated

### 12A.V2 — AI Fallback on Timeout

1. Mock AI provider delays response > 5s
2. Timeout triggers
3. Deterministic decision returned unchanged
4. Audit trail records timeout error
5. `triagemate_ai_fallback_total{reason="timeout"}` incremented

### 12A.V3 — AI Fallback on Low Confidence

1. AI returns confidence 0.50
2. Validator rejects advice (< 0.70 threshold)
3. Deterministic decision returned unchanged
4. Audit trail records rejection with reason

### 12A.V4 — Circuit Breaker

1. Simulate 5 consecutive AI failures
2. Circuit breaker opens
3. Next calls fast-fail to deterministic fallback
4. After 30s, circuit breaker half-open
5. Successful call closes breaker
6. Health endpoint reflects breaker state

### 12A.V5 — Cost Budget Exceeded

1. Set daily budget to $0.01
2. Process events until budget exceeded
3. Subsequent AI calls return `NONE`
4. `triagemate_ai_budget_exceeded_total` incremented
5. Deterministic decisions continue unaffected

### 12A.V6 — AI Disabled

1. Set `triagemate.ai.enabled=false`
2. Process events
3. No AI calls made
4. No AI audit records created
5. Decision pipeline performance identical to Phase 11

---

## Done Criteria

Phase 12A is complete when:

- [ ] `AiDecisionAdvisor` interface implemented with one provider (Anthropic Claude)
- [ ] `AiAdvisedDecisionService` decorator wraps existing `DefaultDecisionService`
- [ ] Structured output parsing with JSON schema validation
- [ ] `AiAdviceValidator` deterministically validates AI advice
- [ ] Deterministic fallback works for all failure modes (timeout, error, low confidence, budget)
- [ ] Circuit breaker protects pipeline from AI provider instability
- [ ] Dedicated AI executor with `AbortPolicy` (not `CallerRunsPolicy`)
- [ ] `ai_decision_audit` table persists every AI interaction
- [ ] Cost tracking per decision with configurable limits
- [ ] Prompt template versioned and hashed
- [ ] Input sanitization prevents PII leakage and prompt injection
- [ ] AI metrics exposed on `/actuator/prometheus`
- [ ] AI health indicator on `/actuator/health/ai`
- [ ] All 6 verification scenarios pass
- [ ] AI can be disabled via config with zero overhead
- [ ] All existing Phase 7-11 tests remain green

---

## Architectural Impact

Phase 12A transforms TriageMate from:

**deterministic decision engine**

into:

**deterministic decision engine + bounded AI advisory layer**

Using the Decorator pattern on `DecisionService`, the AI layer is:
- **Optional** — disabled by config flag
- **Reversible** — remove the decorator, pipeline unchanged
- **Observable** — every call audited and metriced
- **Bounded** — timeout, cost limit, circuit breaker

The existing codebase (`Policy`, `CostGuard`, `DecisionRouter`, `OutboxPublisher`) remains **untouched**.

---

## Version Target

**Release Tag:** `v0.12.0`
