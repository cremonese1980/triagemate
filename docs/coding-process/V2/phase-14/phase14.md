# PHASE 14 — Production Hardening & Scale

## Objective

Prepare TriageMate for **real production workloads** by introducing operational resilience, scalability guarantees, and production-grade observability.

While earlier phases focus on correctness and architecture, Phase 14 focuses on **operational durability** under real-world conditions.

This phase ensures the system can:

- run reliably under sustained load
- survive component failures
- scale horizontally without data loss
- remain observable and debuggable in production environments

---

## Design Principles

### Production safety first
No optimization or feature may compromise system correctness.

### Horizontal scalability
The system must scale by adding instances rather than vertically increasing resources.

### Deterministic behavior under scale
Adding instances must not introduce:
- duplicate decisions
- inconsistent routing
- race conditions

### Observability as a first-class feature
Production debugging must rely on logs, metrics, and traces rather than guesswork.

---

## Sub-phases

---

## 14.1 — Kafka Consumer Scaling

### Goal
Allow TriageMate services to scale horizontally while consuming Kafka topics safely.

### Deliverables
- Explicit Kafka consumer concurrency configuration
- Partition-aware scaling
- Verification that parallel consumers do not produce duplicate decisions
- Consumer lag visibility

### Implementation Tasks

#### 14.1.a — Consumer Concurrency Configuration

Introduce explicit configuration for Kafka listener concurrency.

Example:

```yaml
spring:
  kafka:
    listener:
      concurrency: 3
```

Ensure that concurrency never exceeds partition count.

#### 14.1.b — Partition Awareness

Verify that each partition is consumed by only one consumer instance at a time.

**Acceptance:**
- no duplicate event processing
- no partition ownership conflicts

#### 14.1.c — Consumer Lag Monitoring

Expose Kafka consumer lag metrics.

Metrics should include:
- partition lag
- total lag
- processing throughput

**Acceptance:**
- lag visible via metrics
- alerts possible when lag exceeds threshold

---

## 14.2 — Idempotency Under Horizontal Scale

### Goal
Guarantee that horizontal scaling never produces duplicate decisions.

### Deliverables
- database-backed idempotency guard
- race-condition tests
- verification with multiple instances

### Implementation Tasks

#### 14.2.a — Atomic Idempotency Insert

Use a database constraint to enforce single processing.

Example pattern:

```sql
INSERT INTO processed_events(event_id)
VALUES (?)
ON CONFLICT DO NOTHING
```

**Acceptance:**
- duplicate events are safely ignored
- no race condition under concurrency

#### 14.2.b — Multi-instance Race Test

Run tests with:
- multiple application instances
- identical events

**Verify:**
- exactly one decision produced
- other instances detect duplicate

---

## 14.3 — Resilience & Failure Handling

### Goal
Ensure the system degrades gracefully when dependencies fail.

### Deliverables
- retry policies
- circuit breakers
- timeout handling

### Implementation Tasks

#### 14.3.a — Retry Strategy

Implement retries for transient failures.

Examples:
- temporary DB connectivity issues
- temporary Kafka broker issues

**Acceptance:**
- retry attempts limited
- exponential backoff used

#### 14.3.b — Circuit Breaker

Introduce circuit breaker protection around external systems.

Example tools:
- Resilience4j

**Acceptance:**
- repeated failures open the circuit
- system fails fast instead of blocking

#### 14.3.c — Timeout Enforcement

Every external call must have explicit timeout limits.

**Acceptance:**
- no blocking operations without timeout
- slow dependencies cannot stall the pipeline

---

## 14.4 — Observability & Monitoring

### Goal
Make production debugging straightforward.

### Deliverables
- structured logs
- metrics exposure
- distributed tracing readiness

### Implementation Tasks

#### 14.4.a — Structured Logging

Ensure all logs are JSON structured.

Fields include:
- `requestId`
- `correlationId`
- `eventId`
- service name
- decision outcome

**Acceptance:**
- logs easily parseable by log aggregation systems

#### 14.4.b — Metrics

Expose application metrics.

Examples:
- event processing rate
- decision latency
- AI usage metrics
- Kafka lag

**Acceptance:**
- metrics visible via Prometheus endpoint

#### 14.4.c — Tracing Readiness

Introduce distributed tracing support.

Possible tools:
- OpenTelemetry

**Acceptance:**
- traces correlate requests across services

---

## 14.5 — Graceful Shutdown

### Goal
Prevent data loss during deployments or restarts.

### Deliverables
- consumer pause during shutdown
- in-flight processing completion
- safe resource cleanup

### Implementation Tasks

#### 14.5.a — Shutdown Hooks

Ensure Spring Boot shutdown hooks wait for:
- message processing completion
- DB transaction completion

**Acceptance:**
- no partially processed events

#### 14.5.b — Kafka Consumer Pause

Pause consumers before shutdown.

**Acceptance:**
- no new messages consumed during shutdown window

---

## 14.6 — Production Configuration Discipline

### Goal
Ensure environment configuration is explicit and safe.

### Deliverables
- environment-based configuration
- secrets externalization
- production configuration validation

### Implementation Tasks

#### 14.6.a — Externalized Secrets

Move sensitive values outside the repository.

Examples:
- API keys
- database credentials

**Acceptance:**
- no secrets committed to source control

#### 14.6.b — Environment Profiles

Define environment profiles:
- `dev`
- `docker`
- `prod`

**Acceptance:**
- each environment has clear configuration boundaries

---

## Verification

### 14.V1 — Horizontal Scaling Test

Run system with multiple instances.

**Verify:**
- no duplicate decisions
- consistent event processing

### 14.V2 — Failure Injection

Simulate failures:
- Kafka broker restart
- database temporary unavailability

**Verify:**
- system recovers automatically

### 14.V3 — Load Simulation

Simulate sustained event load.

**Verify:**
- system keeps up with incoming events
- lag remains bounded

---

## Completion Criteria

Phase 14 is complete when:

- ✓ Multiple service instances run safely
- ✓ Kafka scaling validated
- ✓ Idempotency verified under concurrency
- ✓ Resilience mechanisms active
- ✓ Observability fully operational
- ✓ Graceful shutdown verified
- ✓ Production configuration hardened