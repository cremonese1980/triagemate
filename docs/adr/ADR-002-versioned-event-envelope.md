# ADR-002: Why versioned EventEnvelope

## Status
Accepted

## Decision
All inter-service messages are wrapped in a versioned `EventEnvelope`.

## Rationale
- Stable metadata contract for tracing and audits.
- Independent payload evolution with explicit versioning.
- Deterministic routing based on `eventType` + `eventVersion`.

## Consequences
- Mapping layer required at boundaries.
- Teams must maintain version compatibility policy.
