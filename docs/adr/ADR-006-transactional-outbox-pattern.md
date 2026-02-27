# ADR-006 -- Transactional Outbox Pattern (Polling-based)

## Status
Accepted

## Context

Phase 9.2 introduced durable idempotency with claim-first semantics.
However, the pattern had a known crash window: if the application
crashes after DB commit but before Kafka publish, the decision event
is lost. The idempotency guard prevents reprocessing on restart,
so the event is effectively dropped.

This is the classic dual-write problem: writing to two systems
(DB + Kafka) without transactional coordination.

## Decision

Implement the Transactional Outbox pattern using polling-based
publication from the same database.

### How it works

1. Business transaction atomically writes:
   - `processed_events` (idempotency claim)
   - `outbox_events` (PENDING status, contains decision payload)
2. Transaction commits -- DB is the single source of truth.
3. Async `OutboxPublisher` polls `outbox_events` for PENDING rows.
4. Claims batch using `FOR UPDATE SKIP LOCKED` (multi-instance safe).
5. Publishes to Kafka.
6. Marks row as PUBLISHED (or increments retry on failure).

### Why polling (not CDC/Debezium)

- Simpler operational model (no connector infrastructure)
- Same JVM, no additional services
- Sufficient for current scale (thousands of events/day)
- CDC adds complexity justified only at higher throughput

## Schema

```sql
outbox_events (
  id UUID PRIMARY KEY,
  aggregate_type VARCHAR NOT NULL,  -- used as Kafka topic
  aggregate_id VARCHAR NOT NULL,    -- used as Kafka key
  event_type VARCHAR NOT NULL,
  payload JSONB NOT NULL,
  status VARCHAR NOT NULL CHECK (status IN ('PENDING','PUBLISHED','FAILED')),
  publish_attempts INT DEFAULT 0,
  next_attempt_at TIMESTAMP NOT NULL DEFAULT now(),
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  published_at TIMESTAMP,
  lock_owner VARCHAR,
  locked_until TIMESTAMP,
  last_error TEXT
)
```

## Consequences

- No dual-write risk: Kafka publish failure cannot lose events.
- Events are replayable: PENDING rows survive crashes.
- Multi-instance safe: FOR UPDATE SKIP LOCKED prevents duplicate publish.
- Slight latency increase: events published asynchronously (poll interval).
- At-least-once delivery: downstream consumers must remain idempotent.
- Trade-off accepted: polling latency vs operational simplicity.

## Follow-up

- Phase 11: Add outbox metrics (pending count, publish latency, failure rate).
- Future: Evaluate CDC if throughput exceeds polling capacity.
