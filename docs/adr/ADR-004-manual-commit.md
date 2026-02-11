# ADR-004: Why manual commit

## Status
Accepted

## Decision
Kafka consumer uses manual acknowledgment after successful routing.

## Rationale
- Prevents offset commit before decision publication.
- Enables controlled retry semantics.
- Improves operational correctness under transient failures.

## Consequences
- Consumer code must keep side effects explicit and ordered.
- Requires idempotency strategy for redeliveries.
