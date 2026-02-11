# Triagemate Architecture

## Sequence: HTTP → Kafka → Consumer → Kafka

```text
Client -> Ingest API: POST /api/ingest/messages
Ingest API -> Kafka: publish EventEnvelope<InputReceivedV1>
Kafka -> Triage Consumer: deliver input-received.v1
Triage Consumer -> DecisionService: decide(DecisionContext)
DecisionService -> Triage Consumer: DecisionResult
Triage Consumer -> Kafka: publish EventEnvelope<DecisionMadeV1>
Triage Consumer -> Kafka: manual ack offset
```

## Event contract
- Every cross-service message uses `EventEnvelope<T>`.
- Required decision inputs:
  - `eventId`
  - `eventType`
  - `eventVersion`
  - `occurredAt`
  - `trace`
  - `payload`
- `trace` carries `requestId`, `correlationId`, `causationId`.

## Decision pipeline
1. Orchestration validates envelope presence.
2. Idempotency guard checks `eventId`.
3. Context factory maps envelope to `DecisionContext`.
4. Domain `DecisionService` returns explicit `DecisionResult`.
5. Router publishes outcome event.
6. Orchestration records latency metric and acknowledges offset.

## Retry semantics
- Outcome `RETRY` triggers retryable exception.
- Offset is not acknowledged when retryable exception escapes listener.
- Duplicate delivery is suppressed via `eventId` guard.
- Guard currently in-memory (safe for local/dev, replace in prod).
