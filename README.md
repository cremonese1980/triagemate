# Triagemate v1.0 (Control Plane Refactor)

Triagemate is an event-driven decision system: ingest receives HTTP input, triage produces deterministic decisions, and outcomes are emitted as versioned events.

## System purpose
- Accept external operational messages through HTTP.
- Convert requests into auditable events.
- Run a deterministic decision pipeline before any AI augmentation.
- Emit replayable decision events for downstream consumers.

## Architecture overview

```text
┌───────────────┐      HTTP POST       ┌───────────────────┐
│ External App  ├─────────────────────►│ triagemate-ingest │
└───────────────┘                      └─────────┬─────────┘
                                                 │ Kafka publish: input-received.v1
                                                 ▼
                                          ┌──────────────┐
                                          │    Kafka     │
                                          └──────┬───────┘
                                                 │ Kafka consume
                                                 ▼
                                         ┌───────────────────┐
                                         │ triagemate-triage │
                                         │ deterministic rule│
                                         └─────────┬─────────┘
                                                   │ Kafka publish: decision-made.v1
                                                   ▼
                                            ┌──────────────┐
                                            │    Kafka     │
                                            └──────────────┘
```

🧠 Durable Idempotency (Phase 9.2)

## Durable Idempotency (Phase 9.2)

TriageMate implements **database-backed idempotency** to guarantee
duplicate-safe and restart-safe processing.

### Strategy

- Table: `processed_events`
- Unique constraint on `event_id`
- Atomic insert-first pattern:
```sql
INSERT INTO processed_events (event_id, processed_at)
VALUES (?, ?)
ON CONFLICT (event_id) DO NOTHING
RETURNING 1;
```

### Processing Order (claim-first)

1. Validate message
2. Attempt atomic claim (tryMarkProcessed)
3. If already claimed → short-circuit
4. Execute decision logic
5. Publish decision event

### Guarantees

* Duplicate-safe across restarts
* Race-safe across concurrent consumers
* No in-memory state
* PostgreSQL enforces uniqueness

### Known Limitation (resolved in Phase 10)

If the application crashes after claim but before publish,  
the decision event may not be emitted.

This will be eliminated in Phase 10 using the Transactional Outbox pattern.

## Event flow
1. Client calls `POST /api/ingest/messages`.
2. Ingest emits `triagemate.ingest.input-received.v1` with `EventEnvelope`.
3. Triage consumes event, builds `DecisionContext`, runs `DecisionService`.
4. Triage emits `triagemate.triage.decision-made.v1`.
5. Consumer acknowledges manually only after routing succeeds.

## Module responsibilities
- `triagemate-contracts`: event envelope + versioned payload contracts.
- `triagemate-ingest`: HTTP API, validation, envelope creation, Kafka publish.
- `triagemate-triage`: orchestration, deterministic decision engine, outcome routing.

## Versioning strategy
- Contract evolution is append-only.
- Breaking payload changes require event version/type bump.
- Envelope is stable and versioned (`eventVersion`) to preserve replayability.

## Local setup (5 minutes)
1. `cp .env.example .env`
2. `docker compose up --build`
3. Ingest endpoint: `http://localhost:8081/api/ingest/messages`
4. Triage actuator health: `http://localhost:8082/actuator/health`

## Run locally without Docker
- `./mvnw clean test`
- `./mvnw -pl triagemate-ingest spring-boot:run`
- `./mvnw -pl triagemate-triage spring-boot:run`

## Tests
- Full suite: `./mvnw test`
- Triage only: `./mvnw -pl triagemate-triage test`
- Ingest only: `./mvnw -pl triagemate-ingest test`

## Design principles
- SOLID boundaries, framework-free domain model.
- Event-driven orchestration.
- Deterministic-before-AI pipeline.
- Manual commit and explicit retry behavior.
- Structured JSON logs with correlation fields.

## Documentation map
- Architecture details: `docs/architecture.md`
- ADRs: `docs/adr/`
- Logging rules: `docs/logging.md`

## Short roadmap
- Persistent idempotency store (Redis/Postgres) replacing in-memory guard.
- Decision policy packs by domain.
- AI augmentation layer behind deterministic gate.
