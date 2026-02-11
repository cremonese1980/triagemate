# ADR-001: Why Kafka over REST

## Status
Accepted

## Decision
Use Kafka as the integration backbone between ingest and triage.

## Rationale
- Decouples producer/consumer availability.
- Supports replay and audit.
- Enables backpressure handling with consumer groups.
- Aligns with event-driven architecture goals.

## Consequences
- Requires local broker for development.
- Introduces topic and schema governance discipline.
