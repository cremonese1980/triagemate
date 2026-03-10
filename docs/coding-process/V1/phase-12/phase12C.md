# PHASE 12C — AI Governance, Quality & Observability

## State Marker

```yaml
Change ID:    TM-12C
Branch:       feat/phase-12c-ai-governance-quality
Stage:        A (Design)
Owner:        Gabriele
Depends On:   v0.12.1 (Phase 12B)
Target Tag:   v0.12.2

Status:
  prompt_governance:          not_implemented
  quality_metrics:            not_implemented
  confidence_calibration:     not_implemented
  regression_detection:       not_implemented
  model_lifecycle:            not_implemented
  observability_dashboard:    not_implemented
  error_taxonomy:             not_implemented

Tests:
  unit:        pending
  integration: pending
  manual:      pending

Completion Criteria: NOT_MET
```

## Objective

Harden the AI subsystem for production readiness with:
1. **Prompt governance** — versioned, approved, rollback-safe prompts
2. **Quality measurement** — acceptance rate, regression detection
3. **Confidence calibration** — empirical validation of AI confidence scores
4. **Model lifecycle** — version tracking, drift detection, migration path
5. **Observability** — dedicated AI health endpoints, metrics, alerting

Phase 12C does NOT add new AI capabilities. It makes the existing 12A+12B capabilities **production-safe, measurable, and governable**.

---

## Prerequisites

Phase 12B must be complete:
- RAG pipeline operational
- Multi-provider support working
- Audit trail populated with real AI interactions (data needed for quality metrics)

---

## Sub-Phases

## 12C.1 — Prompt Governance

### Goal
Establish controlled prompt evolution with versioning, approval, and rollback.

#### 12C.1.a — Prompt Version Registry

**Flyway migration:**
```sql
CREATE TABLE prompt_templates (
    id              BIGSERIAL PRIMARY KEY,
    prompt_id       VARCHAR(100) NOT NULL,
    version         VARCHAR(20) NOT NULL,
    content         TEXT NOT NULL,
    content_hash    VARCHAR(64) NOT NULL,
    purpose         VARCHAR(100),
    target_models   TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by      VARCHAR(100),
    approved_by     VARCHAR(100),
    approved_at     TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(prompt_id, version)
);
```

**Status lifecycle:**
```
DRAFT --> REVIEW --> APPROVED --> ACTIVE --> DEPRECATED
```

Only one version per `prompt_id` can be `ACTIVE` at a time.

**Acceptance:**
- prompts stored in DB with full version history
- status transitions enforced
- content hash computed and immutable

#### 12C.1.b — Prompt Rollback

If an active prompt degrades quality:
1. Detect degradation via quality metrics (see 12C.2)
2. Mark current version as `DEPRECATED`
3. Reactivate previous version (set status back to `ACTIVE`)
4. Log rollback in audit trail

**Rollback triggers (automated):**
- Advice acceptance rate drops > 30% from 7-day baseline
- Validator rejection rate spikes > 2x baseline

**Acceptance:**
- rollback executable without code deploy
- previous version always available
- rollback event audited

---

## 12C.2 — Quality Metrics

### Goal
Make AI subsystem quality quantifiable through data-driven KPIs.

#### 12C.2.a — Core Quality Metrics

**Metrics computed from `ai_decision_audit` table:**

| Metric | Definition | Target |
|--------|-----------|--------|
| `advisor_acceptance_rate` | accepted advice / total advice given | >= 0.60 |
| `advisor_rejection_rate` | rejected advice / total advice given | <= 0.40 |
| `fallback_rate` | fallback invocations / total invocations | <= 0.05 |
| `avg_latency_ms` | mean AI call latency | <= 1000ms |
| `cost_per_decision_usd` | mean AI cost per decision | <= $0.03 |

**Prometheus metrics (extending 12A metrics):**
```
triagemate_ai_acceptance_rate (gauge, updated hourly)
triagemate_ai_quality_score (gauge, composite metric)
```

**Computation:** scheduled job queries `ai_decision_audit` hourly, updates gauge values.

#### 12C.2.b — Regression Detection

Automatically detect when AI quality degrades.

**Algorithm:**
1. Establish baseline: 7-day moving average of acceptance rate
2. Monitor current: 24-hour rolling window
3. Compare: if current < baseline - threshold, alert

**Thresholds:**
```yaml
triagemate:
  ai:
    regression:
      acceptance-rate-drop: 0.10   # alert if drops >10%
      fallback-rate-increase: 0.05 # alert if increases >5%
      latency-increase-pct: 50     # alert if increases >50%
```

**Alert response:**
- Drop > 10%: WARN log + increment `triagemate_ai_regression_detected_total`
- Drop > 20%: ERROR log + trigger prompt rollback consideration
- Drop > 30%: automatic switch to deterministic-only mode for 10 minutes

**Acceptance:**
- regression detection runs automatically (scheduled)
- alerts actionable
- severe regression triggers automatic mitigation

---

## 12C.3 — Confidence Calibration

### Goal
Ensure AI confidence scores are trustworthy by comparing predicted vs actual accuracy.

#### 12C.3.a — Calibration Data Collection

For every AI decision where outcome is later known:
- Record predicted confidence
- Record whether AI was correct (classification matched actual outcome)
- Bucket by confidence range (0.0-0.2, 0.2-0.4, ..., 0.8-1.0)

**Flyway migration:**
```sql
CREATE TABLE ai_confidence_calibration (
    id                      BIGSERIAL PRIMARY KEY,
    confidence_bucket       VARCHAR(20) NOT NULL,
    predicted_confidence    DOUBLE PRECISION NOT NULL,
    actual_accuracy         DOUBLE PRECISION,
    sample_count            INTEGER DEFAULT 0,
    model_version           VARCHAR(100),
    calibration_period      VARCHAR(20),
    computed_at             TIMESTAMP NOT NULL DEFAULT NOW()
);
```

#### 12C.3.b — Threshold Adjustment

If a confidence bucket is miscalibrated (e.g., AI says 85% confident but only 75% accurate):
- Log miscalibration warning
- Suggest raising acceptance threshold
- Manual review required to adjust (no automatic threshold changes in V1)

**Acceptance:**
- calibration data collected continuously
- miscalibration detected and reported
- threshold adjustment requires human decision

---

## 12C.4 — Model Lifecycle Management

### Goal
Track model versions, detect drift, and manage provider migrations.

#### 12C.4.a — Model Version Tracking

Every AI call already records `model` and `model_version` in `ai_decision_audit` (from 12A).

**Additional monitoring:**
- Alert when model version changes (provider-side silent update)
- Track quality metrics per model version
- Compare versions when drift detected

#### 12C.4.b — Model Drift Detection

Weekly scheduled job:
1. Query quality metrics for current model version
2. Compare to baseline established at model adoption
3. Alert if metrics degrade > 5%

**Drift indicators:**
- Acceptance rate drop vs baseline
- Confidence distribution shift
- Latency change

#### 12C.4.c — Model Migration Strategy

When provider deprecates a model:
1. Evaluate replacement on regression test suite
2. Run parallel (both models, compare results)
3. Switch when confident
4. Update baseline

**Migration does NOT require code changes** — only config:
```yaml
spring:
  ai:
    anthropic:
      chat:
        options:
          model: claude-sonnet-4-20250514  # change this
```

**Acceptance:**
- model version tracked per decision
- version changes detected automatically
- migration is config-only

---

## 12C.5 — Error Taxonomy & Structured Retry

### Goal
Classify AI errors systematically with appropriate retry/fallback strategies.

#### 12C.5.a — Error Classification

Formalize the error categories from 12A into a type hierarchy:

```java
public sealed interface AiException permits
    TransientAiException,    // retry eligible
    PermanentAiException,    // no retry
    BudgetExceededException  // no retry, budget enforcement
{}

public final class TransientAiException extends RuntimeException implements AiException {
    // Network timeout, 429 rate limit, 503 unavailable
}

public final class PermanentAiException extends RuntimeException implements AiException {
    // 401 auth, 400 malformed, schema validation, low confidence
}

public final class BudgetExceededException extends RuntimeException implements AiException {
    // Cost or latency budget exceeded, circuit breaker open
}
```

#### 12C.5.b — Error Budget

Define acceptable AI failure rate:
- Target success rate: 95%
- Warning threshold: 90%
- Critical threshold: 85%

```
triagemate_ai_error_budget_remaining (gauge)
```

When error budget exhausted (< 85% success rate over rolling 1h window):
- Switch to deterministic-only mode
- Log ERROR
- Resume AI after error rate recovers

**Acceptance:**
- every AI error classified into category
- error budget tracked continuously
- budget exhaustion triggers automatic mitigation

---

## 12C.6 — AI Observability

### Goal
Make AI subsystem health visible and debuggable.

#### 12C.6.a — AI Health Endpoint

Extend Spring Boot Actuator:

**Endpoint:** `/actuator/health/ai`

```json
{
  "status": "UP",
  "details": {
    "provider": "anthropic",
    "model": "claude-sonnet-4-20250514",
    "circuitBreaker": "CLOSED",
    "lastSuccessfulCall": "2026-03-10T14:32:11Z",
    "acceptanceRate24h": 0.72,
    "fallbackRate24h": 0.03,
    "dailyCostUsd": 12.45,
    "dailyCostBudgetUsd": 100.00,
    "regressionDetected": false
  }
}
```

**Health logic:**
- `DOWN` if circuit breaker OPEN for > 5 minutes
- `DOWN` if daily cost budget exceeded
- `DOWN` if error budget exhausted
- `DEGRADED` if acceptance rate < 60% or regression detected
- `UP` otherwise

#### 12C.6.b — Alerting Rules

**Critical (immediate action):**
- Circuit breaker open > 5 minutes
- Error budget exhausted (success rate < 85%)
- Daily cost budget exceeded

**Warning (investigate):**
- Acceptance rate drop > 10% from baseline
- Latency p95 > 2s
- Fallback rate > 10%
- Confidence calibration ECE > 0.15

**Info (logged):**
- Model version change detected
- Prompt version deployed
- Quality metrics report generated

**Implementation:** metrics-based alerts via Prometheus alerting rules (no PagerDuty/Slack integration in V1 — defer to V2).

#### 12C.6.c — AI Audit Query Interface

Provide query methods for governance and debugging:

```java
public interface AiAuditQueryService {
    List<AiAuditRecord> findByDecisionId(String decisionId);
    List<AiAuditRecord> findRejectedAdvice(Instant from, Instant to);
    List<AiAuditRecord> findByModelVersion(String modelVersion);
    AiQualitySummary generateQualitySummary(Instant from, Instant to);
}
```

**Quality summary contents:**
- Total AI calls
- Acceptance/rejection breakdown
- Cost summary
- Model versions used
- Fallback reasons breakdown
- Top rejection reasons

**Acceptance:**
- queries performant (< 2s for 30-day range with proper indexing)
- quality summary generatable on demand
- suitable for periodic review

---

## Verification

### 12C.V1 — Prompt Governance

1. Create prompt version 1.1 in DRAFT status
2. Move to REVIEW, then APPROVED, then ACTIVE
3. Verify old version moved to DEPRECATED
4. Simulate quality drop → rollback to v1.0
5. Verify rollback successful, audit trail records it

### 12C.V2 — Quality Regression Detection

1. Establish baseline: 70% acceptance rate over 7 days
2. Simulate drop to 55% acceptance rate
3. Verify WARN alert triggered
4. Simulate drop to 40% acceptance rate
5. Verify deterministic-only mode activated

### 12C.V3 — Confidence Calibration

1. Process 500 decisions with AI
2. Query calibration data
3. Verify confidence buckets populated
4. Identify any miscalibrated buckets (predicted vs actual > 15% gap)
5. Verify miscalibration warning logged

### 12C.V4 — Error Budget

1. Set error budget threshold to 90%
2. Simulate AI failures until success rate drops to 88%
3. Verify budget exhaustion warning
4. Simulate recovery (success rate back to 95%)
5. Verify AI re-enabled

### 12C.V5 — Audit Query

1. Process 100 decisions with AI
2. Query by decision_id → verify full audit record
3. Query rejected advice in last 24h → verify results
4. Generate quality summary → verify all fields populated

---

## Done Criteria

Phase 12C is complete when:

- [ ] Prompt templates stored in DB with version lifecycle
- [ ] Prompt rollback works without code deploy
- [ ] Quality metrics computed automatically (acceptance rate, fallback rate)
- [ ] Regression detection alerts on quality drops
- [ ] Severe regression triggers automatic deterministic-only mode
- [ ] Confidence calibration data collected
- [ ] Miscalibration detected and reported
- [ ] Model version changes detected automatically
- [ ] Error taxonomy with sealed interface hierarchy
- [ ] Error budget tracking with automatic mitigation
- [ ] AI health endpoint reflects subsystem state
- [ ] AI audit query interface operational
- [ ] All 5 verification scenarios pass
- [ ] All Phase 12A and 12B tests remain green

---

## What is Deferred to V2

The following capabilities from the original phase12.md are explicitly deferred:

| Capability | Reason | V2 Phase |
|-----------|--------|----------|
| A/B testing framework with t-test / p-values | Over-engineered for V1; manual comparison sufficient | Phase 14+ |
| Dataset curation pipeline with quality scoring algorithm | Need real data first; start simple | Phase 14+ |
| Dataset health monitoring (coverage, diversity, growth) | Premature without production volume | Phase 14+ |
| Model drift detection with KL divergence | Need calibration data first | Phase 14+ |
| Canary deployments for prompts | Simple rollback sufficient for V1 | Phase 14+ |
| Distributed tracing (Jaeger/Zipkin) | Existing MDC correlation sufficient | Phase 14+ |
| 4 separate Grafana dashboards | Prometheus metrics + health endpoint sufficient | Phase 14+ |
| PagerDuty / Slack integration | Prometheus alerting rules sufficient | Phase 14+ |
| GDPR right-to-erasure automation | Document process, automate later | Phase 14+ |
| Data residency routing per region | Single-region V1 | Phase 16 |
| Multi-tenant AI isolation | Single-tenant V1 | Phase 16 |

---

## Version Target

**Release Tag:** `v0.12.2`
