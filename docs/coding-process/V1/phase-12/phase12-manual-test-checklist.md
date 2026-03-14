# Phase 12 Manual Test Checklist (Recommended)

## Goal

Provide a concise but complete manual verification checklist for Phase 12 (focus on 12A AI advisory MVP behavior).

## Preconditions

- Triage service running with DB + Kafka available.
- Actuator endpoints enabled.
- Two runtime modes available:
  - `triagemate.ai.enabled=false`
  - `triagemate.ai.enabled=true`
- Access to DB query tool and application logs.

## Test scenarios

### M1 — AI disabled baseline
1. Start with `triagemate.ai.enabled=false`.
2. Publish valid input event.
3. Verify decision produced and routed normally.
4. Verify no AI audit row is created.

**Expected:** deterministic pipeline works exactly as pre-AI phases.

---

### M2 — AI happy path (advice accepted)
1. Start with AI enabled and advisor returning valid high-confidence suggestion in allowlist.
2. Publish valid input event.
3. Inspect resulting decision attributes.

**Expected:**
- `aiAdvicePresent=true`
- `aiAdviceAccepted=true`
- `aiAdviceStatus=ACCEPTED`
- AI metadata present (confidence/prompt version/model version when available).

---

### M3 — Advice below confidence threshold
1. Configure advisor to return confidence below `min-confidence-for-suggestion`.
2. Publish event.

**Expected:**
- deterministic outcome unchanged,
- `aiAdviceStatus=REJECTED`,
- rejection reason indicates low confidence.

---

### M4 — Advice classification outside allowlist
1. Keep strict allowlist configured.
2. Return high-confidence classification not in allowlist.

**Expected:** advice rejected, deterministic result preserved, rejection reason indicates invalid classification.

---

### M5 — Advisory (not override)
1. Return valid suggestion with confidence above suggestion threshold but below override threshold.
2. Publish event.

**Expected:** `aiAdviceStatus=ADVISORY`; deterministic outcome unchanged.

---

### M6 — Timeout fallback
1. Force AI response time > advisory timeout.
2. Publish event.

**Expected:**
- deterministic outcome returned,
- fallback metric incremented (`reason=timeout`),
- AI error audit row with timeout error type.

---

### M7 — Circuit breaker fallback
1. Force repeated provider failures until circuit opens.
2. Publish event while open.

**Expected:** immediate deterministic fallback, circuit-breaker fallback metric increment, error audit row with circuit breaker error type.

---

### M8 — Executor saturation fallback
1. Constrain AI executor capacity; create burst traffic.
2. Publish events until queue saturation.

**Expected:** queue-full fallback path activates; no crash; deterministic pipeline remains stable.

---

### M9 — Budget exceeded fallback
1. Lower `max-per-decision-usd` (or daily budget) to trigger cost guard.
2. Publish event.

**Expected:** deterministic fallback; budget-exceeded metric and audit error row recorded.

---

### M10 — Prompt sanitization
1. Include PII-like data (email/phone/card-like patterns) in payload.
2. Execute AI advisory path.
3. Inspect generated prompt payload via controlled logging/test double.

**Expected:** sensitive fragments are masked/redacted before provider invocation.

---

### M11 — Audit completeness
1. Run one success and one error AI interaction.
2. Query `ai_decision_audit`.

**Expected:**
- success row includes provider/model/prompt/version/confidence/latency,
- error row includes error type/message,
- event and decision correlation fields are populated.

---

### M12 — Health endpoint
1. Call `/actuator/health` (or named health component for AI).
2. Check details while AI is healthy, then while circuit is open.

**Expected:** health details expose provider and circuit breaker state coherently.

---

### M13 — Metrics exposure
1. Call `/actuator/prometheus` after running scenarios above.
2. Verify AI metrics.

**Expected metrics present and moving:**
- calls total,
- latency,
- fallback total,
- advice accepted/rejected,
- cost counters,
- circuit breaker state gauge.

---

### M14 — Toggle safety
1. Switch AI enable flag off and restart.
2. Re-run event flow.

**Expected:** AI layer cleanly disabled without side effects on deterministic flow.

---

### M15 — Regression sanity on non-AI flows
1. Execute a standard phase 9–11 flow (idempotency/outbox routing).
2. Compare behavior with pre-12 baseline.

**Expected:** no behavioral regressions in core deterministic pipeline.

## Recommended execution order

Run in sequence: **M1 → M2 → M3/M4/M5 → M6/M7/M8/M9 → M10/M11 → M12/M13 → M14/M15**.

This minimizes setup churn and isolates failures quickly.
