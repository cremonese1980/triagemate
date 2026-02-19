# ADR-005 – Durable Idempotency via Relational Database

## Status
Accepted

## Context

Phase 8.1 introduced idempotent Kafka consumer behavior using
`InMemoryEventIdIdempotencyGuard`.

Limitations of the in-memory approach:

- State is lost on application restart.
- Not safe under horizontal scaling.
- Not crash-consistent.
- Duplicate processing possible after redeploy.
- Not production-grade.

True production-grade idempotency requires a durable, consistent,
and horizontally-safe mechanism.

## Decision

Durable idempotency will be implemented using a relational database
table with a unique constraint on `event_id`.

The table will store processed event identifiers and rely on
database-level uniqueness to guarantee exactly-once side effects.

## Design

Table:

processed_events (
event_id VARCHAR PRIMARY KEY,
processed_at TIMESTAMP NOT NULL
)

Processing flow:

1. Attempt INSERT of event_id.
2. If insert succeeds → process event.
3. If insert fails due to unique constraint → treat as duplicate.
4. No in-memory state relied upon for correctness.

## Alternatives Considered

### Redis (SETNX / atomic script)
Rejected:
- Additional infrastructure dependency.
- Weaker auditability.
- Requires persistence tuning.
- Not transactionally aligned with DB writes.

### Kafka compacted topic
Rejected:
- Increased architectural complexity.
- Harder operational reasoning.
- Not aligned with current project scope.

## Consequences

- Phase 8.2 depends on database introduction (Phase 9).
- Slight latency increase per decision.
- Enables horizontal scaling.
- Enables crash safety.
- Aligns with enterprise-grade architecture.

## Follow-up

- Phase 9: Introduce database infrastructure.
- Phase 8.2: Replace in-memory guard with durable guard.
- Add integration test for restart scenario.
