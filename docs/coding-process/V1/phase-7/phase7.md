# 🔴 PHASE 7 — Consumer & Decision Flow (CORE)

| | |
|---|---|
| **Status** | ✅ DONE (core shipped in v0.5.0) |
| **Priority** | 🔥 HIGH |
| **Dependencies** | Phase 6 (Contracts + Producer) |

---

## 🎯 Objective

Build the first **real decision pipeline** by consuming events and producing decisions.

This is where the system stops being "ingest-only" and becomes **decision-first**.

---

## 🔄 What We Built (Target Flow)

```
HTTP POST /api/ingest/messages
    ↓
triagemate-ingest produces InputReceivedV1
    ↓
Kafka topic: triagemate.ingest.input-received.v1
    ↓
triagemate-triage consumes InputReceivedV1
    ↓
Decision logic executes
    ↓
triagemate-triage produces DecisionMadeV1
    ↓
Kafka topic: triagemate.triage.decision-made.v1
```

---

## 📋 Task Breakdown + Status

### 7.1 Kafka Consumer Setup — ✅ DONE

- `@KafkaListener` in `triagemate-triage`
- Consume from `triagemate.ingest.input-received.v1`
- Deserialize `EventEnvelope<InputReceivedV1>`
- Log incoming events with correlation ID / trace

### 7.2 Event → Domain Model Mapping — ✅ DONE

- Map `InputReceivedV1` → internal domain model
- Preserve trace information (`requestId`, `correlationId`)
- Validate event completeness (core checks)

### 7.3 Deterministic Decision Logic — ✅ DONE

**No AI.** Rule-based, deterministic.

- Category/priority routing
- Deterministic confidence (1.0)
- Motivation/explainability primitives (ReasonCode / DecisionResult semantics)

### 7.4 Produce DecisionMadeV1 — ✅ DONE

- Wrap decision in `EventEnvelope<DecisionMadeV1>`
- Propagate trace information
- Produce to `triagemate.triage.decision-made.v1`
- Log successful production

### 7.5 Error Taxonomy (Core) — ✅ DONE (core primitives)

- Retryable vs Non-retryable modeled at code level (e.g., `RetryableDecisionException` pattern)
- Failure paths exercised in tests

> **Note:** Full operational hardening (backoff strategy, DLQ, idempotency, etc.) is Phase 8.

### 7.6 Logging & Observability (Core) — ✅ DONE (core)

- Key decision points logged
- Correlation/trace propagated
- Structured decision logging primitives in place (governance layer)

---

## 🧪 Testing Strategy — ✅ DONE

### Unit Tests

- Decision logic in isolation
- Event mapping
- Motivation/explainability primitives

### Integration Tests

- End-to-end: POST → Kafka → Consumer → Kafka
- Testcontainers Kafka
- Verify `DecisionMadeV1` is produced
- CI green

---

## ✅ Acceptance Criteria — MET

| Criterion | Status |
|-----------|--------|
| Consumer reads from `input-received.v1` | ✅ |
| Deterministic decisions produced | ✅ |
| `DecisionMadeV1` published to Kafka | ✅ |
| Trace info propagates end-to-end | ✅ |
| Core error classification exists and is test-covered | ✅ |
| Integration tests pass locally and in CI | ✅ |

---

## ⏭️ Deferred (Explicitly) to Phase 8

The following items are **intentionally deferred** to maintain focus on core value delivery:

- Backoff/jitter retry policies and configurable retry boundaries
- DLQ design + implementation
- Idempotency / dedup strategy
- Replay beyond NoOp (operational workflow + persistence hooks)
- Metrics/alerts-grade observability hardening
- Production ergonomics (startup behavior without Kafka, profiles, etc.)

---

## 📝 Notes

> **Phase 7 delivered the core value:** a real decision pipeline.
>
> **Phase 8 will make it safe and operable** under real-world failure modes.
> 
> **Runtime resilience and retry semantics are introduced in Phase 8.**
