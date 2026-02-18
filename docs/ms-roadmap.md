# MS-ROADMAP

## Purpose

Create a repeatable, structured path to build and evolve microservices (ms1, ms2, …) while:

- Refreshing Java fundamentals (Java 8 mindset)
- Adopting modern Java (10 → 21)
- Applying real-world Spring Boot best practices
- Producing AAA-ready, sellable systems, not demos

This roadmap avoids improvisation: **every service follows the same lifecycle, with increasing sophistication.**

---

## Guiding Principle (IMPORTANT)

> **Learning is not isolated from production work.**
>
> We build **real systems**, and we learn deeply while building them, even if that slows us down.
>
> **Correctness, clarity, and architectural discipline are non-negotiable.**

---

## PHASE 0 — Baseline Setup

**Applies to every microservice**

### Objective

Have a clean, predictable starting point for each service.

### Outcome

Service starts, responds, and is debuggable in isolation.

**✅ Status: DONE**

---

## PHASE 1 — Java Core Refresh (Java 8 mindset)

### Objective

Rebuild strong fundamentals in a modern context.

### Outcome

Fluent, confident Java core usage.

**🟡 Status: Practiced and continuously reinforced**

---

## PHASE 2 — Modern Java (10 → 21)

### Objective

Replace old patterns with modern language features.

### Outcome

Shorter, safer, more expressive code.

**🟡 Status: In use**

---

## PHASE 3 — Inter-service Communication

### Objective

Build realistic service-to-service interaction.

### Outcome

Predictable behavior under failure.

**✅ Status: DONE**

---

## PHASE 4 — Observability Foundations

### Objective

Make services debuggable in distributed systems.

### Outcome

Production-grade observability foundations.

**🟡 Status: IN PROGRESS** (minor hardening left)

---

## PHASE 5 — Git Versioning & Refactor Discipline

### Objective

Turn working code into portfolio-grade repositories.

### Outcome

Professional, review-ready codebase.

**🟡 Status: IN PROGRESS**

---

## PHASE 6 — Real-World Refactor (Domain-Driven)

### Objective

Stop looking like demos. Start looking like products.

### Applied

- Domain naming
- Real use cases
- Publicly defensible architecture

### Sub-phases (concrete deliverables)

#### PHASE 6.1 — Shared Contracts Module

- Versioned event contracts
- Stable EventEnvelope
- No framework dependencies

#### PHASE 6.2 — Messaging Backbone (Producer)

- HTTP ingest → Kafka
- Real Kafka producer
- Contracts-first payloads

#### PHASE 6.3 — CI & Multi-module Stability

- GitHub Actions
- Green builds locally and remotely

#### PHASE 6.4 — Integration Test Strategy

- Testcontainers Kafka
- End-to-end POST → Kafka
- Failure path mapped to HTTP 503

### Outcome

Systems that look **real, sellable, and defensible**.

**✅ Status: DONE**

---

## 🔴 PHASE 7 — Consumer & Decision Flow (CORE)

### Objective

Build the first **real decision pipeline** by consuming events and producing decisions.

This is where the system stops being "ingest-only" and becomes **decision-first**.

### Applied (delivered)

- Kafka consumer in `triagemate-triage`
- Consume `triagemate.ingest.input-received.v1`
- Map event → domain model (with trace propagation)
- Deterministic decision logic (no AI yet)
- Produce `triagemate.triage.decision-made.v1`
- Decision logging + motivation/explainability primitives
- Error classification primitives (retryable vs non-retryable at code level)
- End-to-end tests green (local + CI)

### Outcome

End-to-end flow:

```
HTTP ingest → Kafka (input-received) → Consumer (triage) → Kafka (decision-made)
```

**✅ Status: DONE** (core shipped in v0.5.0)

> **Note:** Production hardening is intentionally deferred to Phase 8.

---

## PHASE 8 — Resilience & Operational Hardening

### Objective

Make Phase 7 pipeline safe under failure, overload, and real ops constraints.

### Concepts / Deliverables

- **Retry policy strategy** (backoff, jitter) and *clear boundaries* on retries
- **DLQ design and implementation** (producer + consumer + retention rules)
- **Idempotency strategy** (keys, dedup, exactly-once *expectations vs reality*)
- **Consumer startup behavior** without Kafka (dev ergonomics, fail-fast vs degraded mode)
- **Replay strategy** beyond "NoOp" (operational workflow + minimal persistence hooks)
- **Observability hardening** (metrics, structured audit trails, alertable signals)

### Outcome

Failure-aware, production-safe pipelines.

**⏭ Status: Planned**

---

## PHASE 9 — Persistence & Decision Memory

### Objective

Persist the operational brain of the system.

### Concepts

- PostgreSQL + JPA
- Migrations (Flyway/Liquibase)
- Decision timeline
- Input → decision → outcome

### Outcome

Queryable, auditable decision memory.

**⏭ Status: Planned**

---

## PHASE 10 — Advanced Messaging & Streaming

### Objective

Move from "Kafka works" to "Kafka used correctly".

### Concepts

- Idempotent consumers
- Ordering & keys
- Replay strategies
- DLQ consumers

### Outcome

Operationally mature event-driven system.

**⏭ Status: Planned**

---

## PHASE 11 — Packaging, Deployment & Ops

### Objective

Make the system runnable by someone else.

### Concepts

- Docker (multi-stage)
- docker-compose
- Environment discipline
- CI hardening

### Outcome

Reproducible local & demo environments.

**⏭ Status: Planned**

---

## PHASE 12 — AI Integration (Decision Support)

### Objective

Introduce AI **only** where it adds value.

### Concepts

- Spring AI
- LLM APIs
- AI output = untrusted input
- Approval hooks

### Outcome

AI as a **decision support engine**, not automation.

**⏭ Status: Planned**

---

## AAA TRACK (PARALLEL, IMPORTANT)

### AAA Project — TriageMate (ACTIVE)

AI-driven message triage for SMEs.

- Real product, not a demo
- Decision-first architecture
- Event-driven
- Audit & explainability by default

**This project validates the entire roadmap.**

---

## Rules

1. Phases can overlap
2. Learning depth > speed
3. Refactors are intentional
4. **This file is the single source of truth**

---

## Quick Reference: Phase Status Legend

| Symbol | Status | Meaning |
|--------|--------|---------|
| ✅ | DONE | Complete and stable |
| 🟡 | IN PROGRESS | Active work |
| 🔴 | CORE | Critical current focus |
| ⏭ | Planned | Future work |