# MS-ROADMAP

## Purpose

Create a repeatable, structured path to build and evolve microservices (ms1, ms2, …) while:

- Refreshing Java fundamentals (Java 8 mindset)
- Adopting modern Java (10 → 21)
- Applying real-world Spring Boot best practices
- Producing AAA-ready, sellable systems, not demos

This roadmap avoids improvisation:  
**every service follows the same lifecycle, with increasing sophistication.**

---

## Guiding Principle (IMPORTANT)

> Learning is not isolated from production work.

We build **real systems**, and we learn deeply while building them, even if that slows us down.

**Correctness, clarity, and architectural discipline are non-negotiable.**

---

## PHASE 0 — Baseline Setup

*(applies to every microservice)*

### Objective

Have a clean, predictable starting point for each service.

### Steps

- Spring Boot project (same Boot version across services)
- Java 21 toolchain
- `application.yml` with explicit port
- One health endpoint
- Consistent package structure

### Outcome

Service starts, responds, and is debuggable in isolation.

**✅ Status: DONE**

---

## PHASE 1 — Java Core Refresh (Java 8 mindset)

### Objective

Rebuild strong fundamentals in a modern context.

### Concepts

- Streams (map / filter / reduce)
- Lambdas
- Optional (correct usage, no abuse)
- equals / hashCode contract
- Identity vs ordering (Set vs TreeSet)
- Immutability (manual)

### Applied in services

- Non-trivial stream pipelines
- Deduplication + winner selection
- Correct Optional semantics
- Exercises embedded in real code paths

### Outcome

Fluent, confident Java core usage.

**🟡 Status: Practiced, but intentionally revisited during real projects**

---

## PHASE 2 — Modern Java (10 → 21)

### Objective

Replace old patterns with modern language features.

### Concepts

- `var`
- Text blocks
- `record`
- Pattern matching (instanceof, switch)
- Sealed interfaces / classes
- Modern `java.time`

### Applied in services

- DTOs as records
- Sealed hierarchies for state machines
- Pattern matching in business logic
- Compiler-enforced exhaustiveness

### Outcome

Shorter, safer, more expressive code.

**🟡 Status: In use, but continuously reinforced**

---

## PHASE 3 — Inter-service Communication

### Objective

Build realistic service-to-service interaction.

### Concepts

- RestClient (Spring 6)
- Timeouts
- Error classification
- HTTP status mapping
- Strict vs best-effort calls

### Applied

- `triagemate-ingest` → `triagemate-triage`
- Graceful degradation
- Latency measurement
- Aggregated responses

### Outcome

Predictable behavior under failure.

**✅ Status: DONE**

---

## PHASE 4 — Observability Foundations

### Objective

Make services debuggable in distributed systems.

### Concepts

- Correlation ID
- MDC logging
- Structured (JSON) logs
- Logging discipline (what / when / how)

### Applied

- Request ID propagation `ingest → triage`
- Logs traceable across services
- Clear root cause visibility

### Outcome

Production-grade observability.

**🟡 Status: IN PROGRESS (final hardening ongoing)**
- ✅ correlation id + propagation done
- ✅ MDC logging done
- ⏳ optional: structured JSON logging + final discipline pass

---

## PHASE 5 — Git Versioning & Refactor Discipline

### Objective

Turn working code into portfolio-grade repositories.

### Concepts

- Git hygiene (status / diff / commit)
- Mono-repo with multiple services
- Clear refactor commits
- README as product documentation

### Applied

- `ms1` → `triagemate-ingest`
- `ms2` → `triagemate-triage`
- Single repo, multiple modules
- Clean history
- CI workflow running

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

### Outcome

Systems that look real, sellable, and teachable.

**🟡 Status: IN PROGRESS / PARTIALLY DONE**

### Phase 6 Sub-phases (concrete deliverables)

> We keep PHASE 6 as the **domain-driven umbrella**.  
> The Kafka/contracts work is not a different phase: it is a **deliverable inside Phase 6** because it is part of making the system “real” and defensible.

---

### PHASE 6.1 — Shared Contracts Module (Versioned Events)

#### Objective
Create a stable, boring, versioned contract layer that every service can depend on.

#### Applied
- `triagemate-contracts` module (plain Java JAR)
- `EventEnvelope<T>` (auditable, versioned envelope)
- Versioned payloads under `com.triagemate.contracts.events.v1`
    - `InputReceivedV1`
    - `DecisionMadeV1`
    - `OutcomeRecordedV1`

#### Outcome
A single source of truth for cross-service payloads.

**✅ Status: DONE**

---

### PHASE 6.2 — Kafka Producer in Ingest (Real Backbone)

#### Objective
Stop passing “demo DTOs” and start emitting real, versioned events.

#### Applied
- `triagemate-ingest` POST endpoint accepts input
- Ingest publishes `input-received.v1` to Kafka
- Producer config under `spring.kafka.*`
- Contracts-first payload + envelope

#### Outcome
HTTP ingest → Kafka is real.

**✅ Status: DONE**

---

### PHASE 6.3 — CI + Multi-module Stability

#### Objective
Make the repo reliably green in CI and locally, without hacks.

#### Applied
- GitHub Actions matrix over modules
- Maven wrapper usage per repo rules
- Removed fragile module assumptions
- Fixed “mvnw not found” issues in CI

#### Outcome
Repeatable green builds.

**✅ Status: DONE**

---

### PHASE 6.4 — Test Strategy: Real Integration + Clean Context

#### Objective
Prove the backbone works end-to-end and keep tests deterministic.

#### Applied
- Kafka integration test using Testcontainers (end-to-end: POST → Kafka topic)
- Shared Kafka container base test (reused across tests)
- Removed fake KafkaTemplate wiring from context loads
- Producer config aligned in `application.yml`

#### Outcome
A real, reproducible, production-grade test suite foundation.

**✅ Status: DONE**

---

## PHASE 7 — Resilience Patterns

### Concepts

- Retry
- Backoff
- Circuit breaker (manual → library)

### Applied (target)
- Consumer-side retry taxonomy (retryable vs non-retryable)
- Deterministic error mapping (HTTP + events)
- Minimal dead-letter strategy (design first, then implement)

**⏭ Planned**

---

## PHASE 8 — Persistence & Data Stores

### Concepts

- PostgreSQL + JPA
- Migrations (Liquibase/Flyway)
- Redis
- Testcontainers

### Applied (target)
- Persist the operational trail: input → decision → outcome
- Queryable decision memory (timeline + diffs later)

**⏭ Planned**

---

## PHASE 9 — Messaging & Streaming (Advanced)

### Concepts

- DLQ patterns
- Consumer groups, idempotency, replay
- Kafka “beyond basics” (partitions, keys, ordering guarantees)
- Optional: RabbitMQ only if a use-case demands it

### Applied (target)
- Triage consumes `input-received.v1`
- Produces `decision-made.v1`
- Outcome/events for exception-first UX foundations

**⏭ Planned**

---

## PHASE 10 — Packaging & Deployment

### Concepts

- Docker (multi-stage)
- docker-compose
- CI basics (extend)
- Runtime configuration discipline

### Applied (target)
- Compose stack: service(s) + Kafka (+ Postgres later)
- Reproducible run steps for a stranger

**⏭ Planned**

---

## PHASE 11 — AI Integration

### Concepts

- Spring AI
- OpenAI / LLM APIs
- RAG pipelines
- Cost & latency awareness

### Applied (target)
- AI only at decision nodes (decision support)
- “AI output is untrusted input” validated by deterministic checks
- Approval hooks before execution

**⏭ Planned**

---

## AAA TRACK (PARALLEL, IMPORTANT)

### AAA Project 1 — TriageMate (ACTIVE)

AI-driven message triage for SMEs.

- Built in parallel with Phases 1–11
- Real product, not an exercise
- Production-grade code
- Demo + documentation mandatory

**This project guides and validates the entire roadmap.**

---

## Rules

- Phases can overlap
- Learning depth > speed
- Refactors are intentional
- **This file is the single source of truth**
