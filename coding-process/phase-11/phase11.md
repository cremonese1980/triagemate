# Phase 11 — Observability & Operational Hardening

## 📊 Current State

```
Status:       NOT_STARTED
Phase:        11
Change ID:    TM-11
Stage:        A
Owner:        Gabriele
Branch:       feat/phase-11-observability
Depends On:   v0.10.0
Target Tag:   v0.11.0
```

### Objective

- Structured JSON logging
- End-to-end correlation (requestId + correlationId + eventId)
- Business + infrastructure metrics
- Operational health indicators
- Backlog guardrails

**DoD Status:** `not_met`

---

## 🅐 Problem Statement

The system currently provides:

- Idempotency guarantees
- Transactional outbox pattern
- Crash-safe operations

However, it lacks:

- Audit-grade logging
- Debug-grade traceability
- Operational observability

**Required capabilities:**

- Structured logging
- Robust correlation tracking
- Business metrics
- Infrastructure metrics
- Operational health indicators

---

## 🅑 Implementation Tasks

### 11.1 — Structured Logging Discipline

#### 11.1.a — Enforce JSON logging everywhere
- Configure Logback JSON encoder
- Disable plain text in production-like profiles
- Standardize field naming conventions

#### 11.1.b — Mandatory fields policy
Ensure presence in all logs:

```
requestId
correlationId
eventId (when available)
service
decisionOutcome (when applicable)
```

#### 11.1.c — Log level governance
Define and enforce level policies:

```
ERROR   - Unrecoverable failures
WARN    - Degraded but recoverable behaviour
INFO    - Business state transitions
DEBUG   - Diagnostic information
```

#### 11.1.d — Sensitive data policy
Define and enforce PII/secret scrubbing:

- **Sensitive fields:** patient identifiers, device serial numbers, authentication tokens
- **Mechanism:** Filter in Logback encoder or sanitize before MDC population
- **Verification:** Manual log audit in V3

---

### 11.2 — Correlation & MDC Hardening

#### 11.2.a — Populate MDC at Kafka boundary
In consumer:
- Extract `requestId`, `correlationId`, `eventId` from payload
- Populate MDC before business logic execution

#### 11.2.b — Clear MDC correctly
- Use `try-finally` or filter to clear MDC after message processing
- Prevent leakage between messages in thread pool

#### 11.2.c — Restore MDC in outbox publisher
**Problem:** Publisher runs asynchronously and loses MDC context.

**Solution:**
- Extract trace fields from `payload` JSONB column (contains full `EventEnvelope` with `Trace`)
- Restore MDC from extracted trace before publishing to Kafka
- Pattern: `payload::jsonb->'trace'->'requestId'`

**Schema change required:** NO (payload already contains trace data)

**Note:** Dedicated columns (`request_id`, `correlation_id`) could enable direct SQL queries/indexes but add complexity. Defer unless operational need emerges.

---

### 11.3 — Business Metrics

**Naming convention:** `triagemate_*` prefix with `snake_case` (Prometheus standard)

#### 11.3.a — Decision metrics
Expose:

```
triagemate_decision_total{outcome}       - Counter by outcome (accepted, rejected, duplicate)
triagemate_decision_invalid_total        - Counter for validation failures
```

#### 11.3.b — Decision latency metrics
Extend existing timer:

```
triagemate_decision_latency_seconds{outcome}   - Histogram with percentiles (p50, p95, p99)
```

#### 11.3.c — Metrics exposure
- Expose metrics via `/actuator/prometheus`
- Verify scrape endpoint returns valid Prometheus format

---

### 11.4 — Outbox Metrics

**Naming convention:** `triagemate_*` prefix with `snake_case`

#### 11.4.a — Publish metrics

```
triagemate_outbox_published_total        - Counter for successful publishes
triagemate_outbox_retry_total            - Counter for retry attempts
```

#### 11.4.b — Backlog metrics

```
triagemate_outbox_pending_count          - Gauge for unpublished message count
triagemate_outbox_oldest_message_age_seconds - Gauge for age of oldest pending message
```

#### 11.4.c — Failure metrics

```
triagemate_kafka_publish_failure_total   - Counter for Kafka send failures
triagemate_outbox_validation_failure_total - Counter for payload validation failures
```

---

### 11.5 — Operational Safety

#### 11.5.a — Health indicators
Implement Spring Boot health indicators:

**Kafka connectivity:**
- Status: UP if producer can reach bootstrap servers
- Status: DOWN if connection fails
- Include last successful publish timestamp

**Outbox backlog:**
- Status: UP if `pending_count <= 100`
- Status: DEGRADED if `100 < pending_count <= 500`
- Status: DOWN if `pending_count > 500`
- Include oldest message age in details

#### 11.5.b — Backlog guardrail
**Threshold:** 100 messages

Behavior:
- Log WARN when backlog exceeds 100
- Degrade health status to DEGRADED
- Log ERROR when backlog exceeds 500
- Degrade health status to DOWN

**Rationale:** 100 msg ~ 1 minute of publishing at normal rate; 500 msg indicates severe degradation.

#### 11.5.c — Graceful shutdown verification
Verify during shutdown:
- `OutboxPublisher` stops polling
- In-flight publish attempts complete or timeout
- No `FOR UPDATE SKIP LOCKED` remains held
- Health endpoint returns 503 after shutdown initiated

---

## 🅒 Verification

### 11.V1 — Load sanity test
**Scenario:**
- Ingest 100 events via Kafka
- All events processed successfully

**Verify:**
- `triagemate_decision_total` increments by 100
- `triagemate_outbox_published_total` increments by 100
- `triagemate_outbox_pending_count` returns to 0
- All logs contain `requestId`, `correlationId`, `eventId`

---

### 11.V2 — Failure injection
**Scenario 1: Kafka unavailable**
- Stop Kafka container
- Attempt to publish 10 outbox messages

**Verify:**
- `triagemate_kafka_publish_failure_total` increments by 10
- `triagemate_outbox_retry_total` increments (exponential backoff visible in logs)
- `triagemate_outbox_pending_count` increases by 10
- Health indicator reports DEGRADED or DOWN
- No exceptions crash the publisher thread

**Scenario 2: Kafka recovery**
- Restart Kafka container
- Wait for retry interval

**Verify:**
- Publisher successfully publishes pending messages
- `triagemate_outbox_published_total` increments by 10
- `triagemate_outbox_pending_count` returns to 0
- Health indicator returns to UP

---

### 11.V3 — Log audit review
Manual inspection of production-like logs:

**Traceability:**
- Every decision log contains complete trace (`requestId`, `correlationId`, `eventId`)
- Events can be correlated from ingestion → decision → outbox → publish

**Sensitive data:**
- No patient identifiers in logs
- No device serial numbers in logs
- No authentication tokens in logs

**Signal quality:**
- No noisy INFO logs (polling loops, repetitive framework events)
- Stack traces only on ERROR logs
- Business state transitions clearly visible

---

## 🅓 Done Criteria

**Phase 11 DONE when:**

```
✅ JSON logging enforced in production profiles
✅ MDC propagation robust (including outbox publisher via payload extraction)
✅ Business metrics active (triagemate_decision_total, triagemate_decision_latency_seconds)
✅ Outbox metrics active (triagemate_outbox_pending_count, triagemate_outbox_published_total)
✅ Health indicators present (Kafka, backlog)
✅ Backlog guardrail functional (threshold: 100/500)
✅ Sensitive data policy enforced
✅ All verification scenarios pass
✅ CI green
```

---

## 💬 Editorial Note

**ChatGPT is inadequate for this type of work.** Its inability to maintain context, handle technical depth consistently, and provide production-grade guidance makes it unsuitable for complex engineering tasks requiring precision and reliability.