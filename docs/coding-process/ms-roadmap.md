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

### Sub-phases (delivered)

#### PHASE 8.1 — In-Memory Idempotency

- Stopgap idempotency guard (ConcurrentHashMap)
- Duplicate detection
- Basic replay hooks

### Outcome

Failure-aware, production-safe pipelines.

**✅ Status: DONE** (v0.8.2 — error classification, retry/backoff, DLT, structured logging)

---

## PHASE 9 — Persistence & Decision Memory

### Objective

Persist the operational brain of the system.

### Concepts

- PostgreSQL + JPA
- Migrations (Flyway/Liquibase)
- Decision timeline
- Input → decision → outcome

### Sub-phases

#### PHASE 9.1 — Runtime Stabilization (Docker + DB wiring)

- PostgreSQL integration
- Profile discipline (dev/docker/test)
- Cold-start verification
- Health indicators

**✅ Status: DONE** (v0.9.1)

#### PHASE 9.2 — Durable Idempotency

- `processed_events` table
- DB-backed idempotency guard
- Race-condition validation
- Restart-safe duplicate detection

**✅ Status: DONE** (v0.9.2)

### Outcome

Restart-safe, duplicate-safe pipeline with persistent event memory.

**✅ Status: DONE** (v0.9.2)

---

## PHASE 10 — Transactional Outbox Pattern

### Objective

Eliminate dual-write risk and achieve consistency-safe event publication.

### Concepts

- Dual-write elimination
- `outbox_events` table
- Atomic business + outbox transaction
- Async publisher loop (polling-based)
- Retry semantics
- Exactly-once practical guarantees

### Outcome

Consistency-safe event publication. No lost events on crash.

**✅ Status: DONE** (v0.10.0)

---

## PHASE 11 — Observability & Operational Hardening

### Objective

Make the system production-observable and operationally mature.

### Concepts

- Structured JSON logging (all services)
- MDC correlation discipline (trace/span propagation)
- Business + outbox metrics (Prometheus/Micrometer)
- Health indicators (DB, Kafka, circuit breakers)
- Backlog guardrails (alert thresholds, auto-backpressure)

### Outcome

Full operational visibility and alertable signals.

**⏭ Status: Planned**

---

# PHASE 12 — AI Decision Support Engine

## Objective

Introduce AI into TriageMate as a governed decision support layer while preserving the deterministic architecture of the system.

This phase transforms TriageMate from a purely deterministic pipeline into a **deterministic engine augmented by AI reasoning**, while maintaining replayability, auditability, and policy authority.

**This phase defines the core identity of TriageMate.**

---

## Architectural Principles

> **AI suggests. Policy validates. System decides.**

- **Deterministic-first:** The deterministic policy engine remains the authoritative decision maker.
- **AI as advisory layer:** AI produces suggestions but never executes decisions directly.
- **Validator-controlled overrides:** AI suggestions may influence the final outcome only after deterministic validation.
- **Replay-safe:** Decisions must remain reproducible even if AI behavior changes.
- **Bounded:** Every AI call has timeout, cost limit, circuit breaker, and schema validation.

---

## Structure

Phase 12 is split into three incremental sub-phases that each deliver standalone value:

### PHASE 12A — AI Advisory Core (MVP) `v0.12.0`

Working AI advisory pipeline with one provider, full auditability, deterministic fallback.

**Key deliverables:**
- `AiDecisionAdvisor` interface (provider-neutral)
- `AiAdvisedDecisionService` decorator wrapping existing `DefaultDecisionService`
- Spring AI integration with one provider (Anthropic Claude)
- Versioned prompt template (classpath resource, hashed)
- Structured JSON output parsing with schema validation
- `AiAdviceValidator` — deterministic validation of AI advice
- Deterministic fallback for all failure modes
- Circuit breaker (Resilience4j) + dedicated executor (`AbortPolicy`)
- `ai_decision_audit` table for full AI traceability
- Cost tracking per decision with configurable limits
- Input sanitization (PII + prompt injection)
- AI metrics on `/actuator/prometheus`
- AI health indicator on `/actuator/health/ai`
- AI disableable via `triagemate.ai.enabled=false` (zero overhead)

**Integration approach:** Decorator pattern on `DecisionService`. Existing code (`Policy`, `CostGuard`, `DecisionRouter`, `OutboxPublisher`) remains **untouched**.

> Detailed spec: `docs/coding-process/V1/phase-12/phase12A.md`

---

### PHASE 12B — RAG + Multi-Provider `v0.12.1`

Historical context enrichment and provider flexibility.

**Key deliverables:**
- Curated Decision Explanation dataset (not raw events)
- Embedding pipeline + embedding cache
- PostgreSQL pgvector for vector storage
- Retrieval service (top-k similarity search)
- Context injection into AI prompts (bounded token budget)
- Ollama integration (local model support)
- Hybrid provider routing (local/cloud based on privacy level)
- Advanced PII handling with privacy classification

> Detailed spec: `docs/coding-process/V1/phase-12/phase12B.md`

---

### PHASE 12C — AI Governance, Quality & Observability `v0.12.2`

Production hardening: make AI measurable, governable, and self-healing.

**Key deliverables:**
- Prompt governance (DB-stored, versioned, rollback-safe)
- Quality metrics (acceptance rate, fallback rate, cost per decision)
- Regression detection with automatic mitigation
- Confidence calibration (predicted vs actual accuracy)
- Model lifecycle (version tracking, drift detection, migration path)
- Sealed error type hierarchy with error budget tracking
- AI health endpoint with detailed subsystem status
- AI audit query interface for governance review

**Note:** Phase 12C can run after Phase 13 if needed. It does not add AI capabilities — it hardens existing ones.

> Detailed spec: `docs/coding-process/V1/phase-12/phase12C.md`

---

## Outcome

At the end of Phase 12 the system becomes:

- a **deterministic decision engine**
- enhanced by **bounded AI advisory reasoning**
- capable of **semantic retrieval** over historical decisions
- operating with **strict governance, safety, and auditability**
- **measurable** through quality metrics and calibration
- **self-healing** through circuit breakers, error budgets, and regression detection

AI improves the quality of decisions while the deterministic system preserves correctness and reproducibility.

---

**⏭ Status:** Planned

---

## PHASE 13 — Decision Versioning & Replay

### Objective

Make decisions auditable, versioned, and replayable for governance.

### Concepts

- Persist full decision artifacts (input snapshot, policy version, outcome)
- Policy version tracking (semantic versioning)
- Replay engine (recalculate decisions with current policy)
- Drift detection (compare old vs new policy outcomes)
- Explainability persistence (human-readable reasons)

### Outcome

Governance-grade decision engine with replay capability.

**Closes V1.0 scope.**

**⏭ Status: Planned**

---

# 🎯 TRIAGEMATE V1.0 COMPLETION POINT

**TriageMate V1.0 is considered complete at the end of Phase 13.**

At that point the system guarantees:

✅ **Durable idempotency** (Phase 9.2)  
✅ **Transactional consistency** (Phase 10 — Outbox pattern)  
✅ **Production-grade observability** (Phase 11)  
✅ **AI-powered decision support** (Phase 12 — Core identity)  
✅ **Decision versioning & replay** (Phase 13 — Governance)

**V1.0 is production-ready for:**
- Single-region deployment
- Moderate load (thousands of decisions/day)
- Governance & audit requirements
- AI-assisted intelligent routing

---

# 🚀 TRIAGEMATE V2.0 (FUTURE)

**Phases 14–18 extend the system into a full distributed governance platform.**

---

## PHASE 14 — Horizontal Scalability & Concurrency Control (HARDCORE)

### Objective

Production-grade horizontal scaling with correctness under extreme load.

### Concepts

- **Multi-instance consumer discipline** (partition ownership, rebalancing)
- **DB race validation** (concurrent duplicate handling)
- **SELECT FOR UPDATE SKIP LOCKED** (outbox concurrent publish)
- **Backpressure tuning** (batch sizing, poll intervals)
- **Spike simulation** (1000+ events, memory safety)
- **Resource hardening** (JVM tuning, pool exhaustion prevention)

### Outcome

System scales horizontally to **tens of thousands of decisions/day** with correctness guarantees.

**⏭ Status: V2.0 — Planned**

---

## PHASE 15 — Policy Engine Isolation & Versioned Rule Sets

### Objective

Separate policy logic from execution engine for hot-swappable rule sets.

### Concepts

- Policy DSL or rules engine (Drools/custom)
- Hot reload of policy versions
- A/B testing framework
- Policy simulation sandbox

### Outcome

Non-developers can author and test policies.

**⏭ Status: V2.0 — Planned**

---

## PHASE 16 — Multi-Tenant Governance

### Objective

Support multiple organizations with isolated decision contexts.

### Concepts

- Tenant isolation (DB, Kafka topics)
- Per-tenant policy versions
- Tenant-specific AI models
- Billing & usage tracking

### Outcome

SaaS-ready TriageMate.

**⏭ Status: V2.0 — Planned**

---

## PHASE 17 — Tamper-Proof Audit Ledger

### Objective

Immutable audit trail for regulatory compliance.

### Concepts

- Event sourcing
- Append-only decision log
- Cryptographic signing
- Blockchain integration (optional)

### Outcome

Regulatory-grade audit trail.

**⏭ Status: V2.0 — Planned**

---

## PHASE 18 — Decision Simulation Sandbox

### Objective

Test policies on historical data without affecting production.

### Concepts

- Replay engine at scale
- What-if analysis
- Policy impact prediction
- Drift analytics dashboard

### Outcome

Risk-free policy evolution.

**⏭ Status: V2.0 — Planned**

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
5. **V1.0 scope is frozen at Phase 13**
6. **V2.0 phases are aspirational, not committed**

---

## Quick Reference: Phase Status Legend

| Symbol | Status | Meaning |
|--------|--------|---------|
| ✅ | DONE | Complete and stable |
| 🟡 | IN PROGRESS | Active work |
| 🔴 | CORE | Critical current focus |
| ⏭ | Planned | Future work |

---

**Document Version:** 2.0  
**Last Updated:** 2026-03-05
**V1.0 Target Completion:** Phase 13
**Next Review:** After Phase 11 completion