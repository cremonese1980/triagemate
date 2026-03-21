# TriageMate

**Deterministic decision engine with AI advisory, replay governance, and full audit trail.**

A production-grade event-driven system where policy decides, AI advises, and every decision is versioned, persisted, and replayable.

---

## Why This Exists

Most decision systems in production share the same problems: they can't explain why a decision was made, they can't reproduce it, and AI is entangled with business logic in ways that make governance impossible.

TriageMate is built around a different premise: **deterministic policy is the source of truth**. AI participates as a controlled advisory layer — never as the decision authority. Every decision is linked to a policy version, persisted with full context, and replayable with drift detection.

This matters in domains like fintech, compliance, fraud detection, and operations — anywhere you need to answer "why did the system decide this, and would it decide the same thing today?"

---

## Architecture

```
                         ┌───────────────────┐
   HTTP POST ──────────► │ triagemate-ingest │
                         └────────┬──────────┘
                                  │ Kafka: input-received.v1
                                  ▼
                         ┌────────────────────┐
                         │  triagemate-triage  │
                         │                     │
                         │  ┌───────────────┐  │
                         │  │ Policy Engine │  │  ← deterministic decisions
                         │  └───────┬───────┘  │
                         │          │          │
                         │  ┌───────▼───────┐  │
                         │  │  AI Advisory  │  │  ← optional, never overrides
                         │  └───────┬───────┘  │
                         │          │          │
                         │  ┌───────▼───────┐  │
                         │  │  Persistence  │  │  ← decisions + outbox (atomic)
                         │  └───────────────┘  │
                         └────────┬────────────┘
                                  │ Kafka: decision-made.v1
                                  ▼
                           Downstream consumers
```

### Module Responsibilities

| Module | Role |
|---|---|
| `triagemate-contracts` | Versioned event envelope and payload definitions. No framework dependencies. |
| `triagemate-ingest` | HTTP API → validation → Kafka publish. Zero business logic. |
| `triagemate-triage` | Decision engine, AI advisory layer, persistence, replay, event emission. |

---

## Key Engineering Decisions

### Deterministic-First, AI-Second

The decision pipeline runs deterministic policy logic first. AI classification (via OpenAI, Anthropic, or local Ollama models) is layered on top through a decorator pattern — it can enrich a decision but never override policy. Replay always uses the deterministic path, guaranteeing `same input + same policy = same output`.

### Transactional Outbox (No Dual-Write)

Decision persistence and outbox writes happen in a single database transaction. An async publisher polls the outbox with `SELECT ... FOR UPDATE SKIP LOCKED` for concurrency-safe emission. No event loss on crash, no distributed transaction coordination.

### Durable Idempotency

A `processed_events` table with a unique constraint on `event_id` provides atomic claim semantics via `INSERT ... ON CONFLICT DO NOTHING`. Duplicate-safe, restart-safe, race-condition-safe — without distributed locks.

### Policy Versioning and Replay

Every decision record includes the `policyVersion` that produced it. The replay engine re-executes historical decisions against their original policy and detects drift. This enables audit, compliance verification, debugging production issues, and safe validation of policy changes before rollout.

### AI Cost Governance

AI calls are gated by configurable budget limits with thread-safe cost tracking (`AtomicLong`-based). The system degrades gracefully — if the budget is exhausted, decisions continue without AI enrichment.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.4 |
| Messaging | Apache Kafka (Confluent 7.6) |
| Database | PostgreSQL + pgvector |
| AI Integration | Spring AI — OpenAI, Anthropic, Ollama (local GPU) |
| Containerization | Docker, Docker Compose |
| Testing | JUnit 5, Testcontainers, Spring Kafka Test |
| CI | GitHub Actions |
| Build | Maven (multi-module) |

---

## Quick Start

### With Docker

```bash
cp .env.example .env
docker compose up --build
```

### Send a message

```bash
curl -X POST http://localhost:8081/api/ingest/messages \
  -H "Content-Type: application/json" \
  -d '{
    "inputId": "input-001",
    "channel": "email",
    "subject": "Device issue",
    "text": "The device has a blinking LED",
    "from": "user@example.com"
  }'
```

### What happens

1. Ingest validates and publishes `input-received.v1` to Kafka
2. Triage consumes the event, runs deterministic policy, optionally enriches with AI
3. Decision is persisted with full context (policy version, reasoning, snapshot)
4. `decision-made.v1` is emitted via transactional outbox
5. Consumer offset is acknowledged only after successful persistence

### Endpoints

| Service | URL |
|---|---|
| Ingest API | `http://localhost:8081/api/ingest/messages` |
| Triage Health | `http://localhost:8082/actuator/health` |

### Without Docker

```bash
./mvnw clean test                                  # run all tests
./mvnw -pl triagemate-ingest spring-boot:run       # start ingest
./mvnw -pl triagemate-triage spring-boot:run       # start triage (separate terminal)
```

---

## Testing

```bash
./mvnw test                           # full suite
./mvnw -pl triagemate-triage test     # triage only
./mvnw -pl triagemate-ingest test     # ingest only
```

Integration tests use Testcontainers — Kafka and PostgreSQL are started automatically in Docker. No external infrastructure required.

---

## Design Principles

- **Framework-free domain model** — Core decision logic has zero Spring dependencies. Pure Java, fully testable in isolation.
- **Event-driven orchestration** — Services communicate exclusively through versioned Kafka events. No synchronous coupling.
- **Explicit error taxonomy** — Retryable vs. non-retryable failures are first-class concepts, not afterthoughts.
- **Manual offset commit** — Kafka offsets are acknowledged only after successful processing. No silent data loss.
- **Structured logging with correlation IDs** — Every log entry carries `requestId`, `correlationId`, and `causationId` for full distributed traceability.
- **Append-only contract evolution** — Breaking payload changes require a new event version. Existing consumers never break.

---

## Target Domains

- Fraud detection pipelines
- Ticket and alert triage
- Compliance and audit systems
- AI governance layers
- Event-driven business rules engines

---

## Known Limitations

- Replay does not yet restore full envelope context — timestamp and metadata are partially reconstructed. Planned improvement for full determinism guarantees.

---

## Documentation

| Document | Path |
|---|---|
| Architecture | `docs/architecture.md` |
| ADRs | `docs/adr/` |
| Logging discipline | `docs/logging.md` |

---

## Roadmap

- RAG over decision memory (pgvector) — in progress
- Policy DSL / rule engine abstraction
- Full replay context restoration
- Multi-tenant decision engines
- Confidence calibration for AI advisory