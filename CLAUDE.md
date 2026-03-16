# CLAUDE.md — Project context for AI assistants

## Project overview

TriageMate is an event-driven microservice system for AI-assisted message triage.
Java 21 / Spring Boot 3.4.0 / Spring AI 1.0.0-M6 / Resilience4j / PostgreSQL / Kafka.

## Modules

| Module | Purpose | Port |
|---|---|---|
| `triagemate-ingest` | HTTP API, message normalization, Kafka producer | 8081 |
| `triagemate-triage` | Decision engine, AI advisory, outbox, idempotency | 8082 |
| `triagemate-contracts` | Event envelope and versioned DTOs | — |
| `triagemate-test-support` | Shared test utilities (Testcontainers base classes) | — |

## Build & test commands

```bash
# Full build
./mvnw clean verify

# Run all tests
./mvnw test

# Single module tests
./mvnw -pl triagemate-triage test
./mvnw -pl triagemate-ingest test

# Run locally with Docker
docker compose up --build

# Specific test class
./mvnw -pl triagemate-triage test -Dtest=AiAdvisedDecisionServiceTest
```

## Commit convention

Format: `type(scope): short summary`

- **Types:** feat, fix, refactor, test, docs, chore, build, ci
- **Scopes:** ingest, triage, contracts, logging, docs, ci, repo, ai
- Imperative mood, lowercase, ≤72 chars, no trailing period

Examples:
```
feat(ai): add budget exceeded fallback integration test
test(ai): verify audit completeness for success and error paths
fix(triage): handle empty decision result
```

## Architecture

```
HTTP → triagemate-ingest → Kafka → triagemate-triage → Decision → Kafka
                                         ↓
                                    AI Advisory (optional)
                                         ↓
                                    PostgreSQL (idempotency + outbox + audit)
```

### Key patterns
- **Deterministic-before-AI**: rules execute first, AI is advisory only
- **Transactional outbox**: atomic writes to processed_events + outbox_events
- **Durable idempotency**: database-backed via processed_events table
- **Circuit breaker + retry**: Resilience4j wraps AI calls
- **Budget guardrails**: per-decision and daily cost limits
- **Manual Kafka commit**: offset acknowledged after routing succeeds

## AI configuration (triagemate.ai.*)

| Property | Default | Description |
|---|---|---|
| `enabled` | false | Master toggle |
| `provider` | anthropic | AI provider (anthropic/ollama) |
| `cost.max-per-decision-usd` | 0.05 | Per-decision cost limit |
| `cost.max-daily-usd` | 100.00 | Daily budget cap |
| `cost.estimated-cost-usd` | 0.003 | Pre-call cost estimate |
| `validation.min-confidence-for-suggestion` | 0.70 | Below → rejected |
| `validation.min-confidence-for-override` | 0.85 | Above → can override |
| `timeouts.advisory` | 5s | AI call timeout |

## Testing infrastructure

- **Testcontainers**: PostgreSQL 16 via `JdbcIntegrationTestBase`
- **Test profiles**: `application-test.yml` (tighter budgets, AI disabled)
- **Test doubles**: `TestAiAuditService`, `StubAiAdvisor`, `StubDecisionService`
- **Integration tests**: `*IT.java` suffix, extend `JdbcIntegrationTestBase`
- **Unit tests**: `*Test.java` suffix, in-memory `SimpleMeterRegistry`

## Key packages (triagemate-triage)

```
com.triagemate.triage.control.ai      — AI advisory, audit, cost, metrics
com.triagemate.triage.control.decision — Core decision engine, policies
com.triagemate.triage.control.routing  — Outcome routing
com.triagemate.triage.persistence      — Idempotency, outbox repositories
com.triagemate.triage.kafka            — Kafka consumers/producers
```

## Definition of Done

- Code compiles, all tests pass
- No dead/commented code
- Structured logging discipline followed
- No secrets committed
- Docs updated if behavior changes
