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

**🟡 Status: IN PROGRESS** (8.1 done, DLQ/retry pending)

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

**🟡 Status: IN PROGRESS**

### Outcome

Restart-safe, duplicate-safe pipeline with persistent event memory.

**🟡 Status: IN PROGRESS** (9.1 done, 9.2 active)

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

**⏭ Status: Planned** (depends on v0.9.2)

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

## PHASE 12 — AI Integration (Decision Support)

### Objective

Introduce AI **where it adds value** for intelligent decision support.

**This is the core identity of TriageMate.**

### Concepts

AI is introduced as **decision support**, not blind automation:

- **AI-assisted classification** (error categorization, routing hints)
- **RAG over Decision Memory** (query "why did we decide X?", grounded in historical data)
- **Local GPU option** (Ollama/local embeddings) + fallback cloud (OpenAI/Claude)
- **AI safety gates** (cost/latency budgets, allowlists, audit fields)
- **Approval hooks** (AI output = untrusted input requiring validation)

### Sub-phases

#### PHASE 12.1 — AI-Assisted Classification

- Spring AI integration
- LLM API calls (OpenAI/Claude)
- Cost/latency budgeting
- Fallback to rule-based logic

#### PHASE 12.2 — RAG over Decision Memory

- Vector embeddings (local or cloud)
- Semantic search over historical decisions
- Context-aware decision explanations

#### PHASE 12.3 — Local GPU Support (Optional)

- Ollama integration
- Local embedding models
- Offline capability

#### PHASE 12.4 — AI Safety & Governance

- Input sanitization
- Output validation
- Cost tracking per decision
- Audit trail for AI usage

#### PHASE 12.5 — Scale Sanity (Minimal Concurrency)

**Purpose:** Prevent system from exploding under load, without full production hardening.

**Deliverables:**
- Kafka consumer concurrency config (explicit, documented)
- Basic rebalance test (2 instances, no data loss)
- Connection pool sizing (Hikari + Kafka alignment)
- Graceful shutdown validation

**NOT in scope:** Advanced locking, spike testing, multi-datacenter — that's V2.0.

### Outcome

AI as a **decision support engine** with operational guardrails.

**⏭ Status: Planned**

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
**Last Updated:** 2026-02-24  
**V1.0 Target Completion:** Phase 13  
**Next Review:** After Phase 10 completion