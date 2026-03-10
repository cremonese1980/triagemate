# 🟠 PHASE 8 — Minimal Resilience Hardening

| | |
|---|---|
| **Status** | DONE |
| **Priority** | 🔥 HIGH |
| **Dependencies** | Phase 7 (Core Decision Flow) |

---

## 🎯 Objective

Make the decision pipeline **failure-aware** and **operationally safe** without introducing advanced production complexity.

This phase introduces controlled retry behavior, minimal DLQ handling, and explicit failure classification — **without breaking determinism**.

---

## 📦 Scope (Minimal Hardening Only)

### ✅ We will:

- Introduce explicit retry classification integration
- Add a minimal Dead Letter Topic strategy
- Add controlled retry/backoff configuration
- Prevent infinite failure loops
- Improve failure observability

### ❌ We will NOT:

- Implement circuit breakers
- Implement idempotency store
- Implement replay orchestration
- Implement advanced streaming semantics

---

## 📋 Task Breakdown

---

## 🔢 Recommended Execution Order

1️⃣ **8.1** → Error Classification  
2️⃣ **8.3** → Configuration (Retry/Backoff)  
3️⃣ **8.2** → Dead Letter Topic (DLT)  
4️⃣ **8.4** → Failure Logging  
5️⃣ **8.5** → Integration Tests

### Why this order?

1. **First**, define the behavior (retryable vs non-retryable)
2. **Then**, make it configurable (retry attempts, backoff)
3. **Then**, protect against poison pills (DLT)
4. **Then**, add observability (structured logging)
5. **Finally**, validate with hardening tests

---

## 🌿 Branch Strategy

| Task | Branch Name |
|------|-------------|
| 8.1 Error Classification | `feat/phase-8-error-classification` |
| 8.3 Retry Configuration | `feat/phase-8-retry-config` |
| 8.2 Dead Letter Topic | `feat/phase-8-dlt` |
| 8.4 Failure Logging | `feat/phase-8-failure-logging` |
| 8.5 Integration Tests | `test/phase-8-integration` |

> **Tip:** Each branch should be merged sequentially to maintain incremental stability.

### 8.1 Runtime Error Classification Integration

**Goal:** Make `RetryableDecisionException` operationally meaningful.

**Tasks:**

- Ensure Kafka listener:
    - Throws `RetryableDecisionException` for transient failures
    - Throws non-retryable exceptions for malformed payloads
- Configure Spring Kafka error handler:
    - Retry N times (configurable)
    - Backoff between retries

#### 🔹 Subtasks

##### 8.1.1 Exception Strategy Definition

Define clear mapping:

- Network / broker issues → retryable
- Serialization issues (recoverable) → retryable
- Malformed payload → non-retryable
- Ensure no generic `Exception` swallowing

##### 8.1.2 Listener Refactor

- Wrap decision execution in controlled try/catch
- Re-throw only classified exceptions
- Ensure no silent failure path

##### 8.1.3 Kafka ErrorHandler Configuration

- Replace default error handler with `DefaultErrorHandler`
- Attach retry policy
- Ensure retry does not block consumer thread indefinitely

##### 8.1.4 Negative Test Validation

- Simulate retryable exception
- Verify retry attempt count increments

**Deliverable:**

```
Consumer behavior:
  retryable     → retried
  non-retryable → sent to DLT
```

---

### 8.2 Minimal Dead Letter Topic (DLT)

**Goal:** Prevent poison-pill infinite loops.

**Tasks:**

- Define topic: `triagemate.triage.decision-made.dlt.v1`
- Configure:
    - `DeadLetterPublishingRecoverer`
    - Attach original headers (`correlationId`, `requestId`)
    - Include failure reason in header
- Log structured event when DLT publish happens

#### 🔹 Subtasks

##### 8.2.1 Topic Naming Discipline

- Ensure topic versioned
- Document naming convention

##### 8.2.2 Recoverer Wiring

- Configure `DeadLetterPublishingRecoverer`
- Ensure partition strategy deterministic

##### 8.2.3 Header Propagation

Preserve:

- `correlationId`
- `requestId`
- `original-topic`
- `exception-class`

Add custom header: `failure-type`

##### 8.2.4 Infinite Loop Protection

- Ensure DLT topic is NOT consumed by same listener
- Validate no re-processing cycle possible

##### 8.2.5 DLT Log Event

Log structured JSON event:

- `"event": "DLT_PUBLISH"`
- Include metadata

**Deliverable:**

Malformed or permanently failing messages end in DLT.

---

### 8.3 Configurable Backoff & Retry

**Goal:** Make retry behavior visible and tunable.

**Tasks:**

- Add configuration in `application.yml`:

```yaml
triagemate:
  kafka:
    retry:
      attempts: 3
      backoff-ms: 1000
```

- Wire configuration into Kafka error handler

#### 🔹 Subtasks

##### 8.3.1 Configuration Properties Class

- Create `KafkaRetryProperties`
- Bind with `@ConfigurationProperties`
- Validate positive values

##### 8.3.2 ErrorHandler Wiring

- Inject properties
- Configure:
    - `FixedBackOff` OR `ExponentialBackOff`
- Ensure retry count respected

##### 8.3.3 Configuration Test

- Override values in test profile
- Assert custom retry config applied

##### 8.3.4 Fail-Fast Guard

- Prevent negative or zero retry attempts
- Add validation constraints

**Deliverable:**

Retry behavior is explicit, not implicit.

---

### 8.4 Failure Logging Discipline

**Goal:** Improve failure observability without log noise.

**Tasks:**

Structured JSON log on:

- Retry attempt
- Retry exhausted
- DLT publish

**Include:**

- `correlationId`
- `requestId`
- `reasonCode` (if available)
- `exception` type
- `retryCount`

#### 🔹 Subtasks

##### 8.4.1 Log Schema Definition

- Define consistent JSON structure
- Ensure field naming consistency

##### 8.4.2 Retry Attempt Logging

- Log before retry execution
- Include attempt number

##### 8.4.3 Retry Exhausted Logging

- Log when retry limit reached
- Mark event type: `RETRY_EXHAUSTED`

##### 8.4.4 DLT Publish Logging

- Log only once per failed event
- Avoid duplicate logs

##### 8.4.5 Log Noise Control

- Ensure no INFO spam
- Failures logged at WARN or ERROR appropriately

**Deliverable:**

Full traceability under failure.

---

### 8.5 Minimal Integration Tests

Add tests for:

1. Retryable exception → retried
2. Non-retryable exception → goes to DLT
3. Retry exhausted → DLT
4. Headers preserved in DLT message

Use **Testcontainers Kafka**.

#### 🔹 Subtasks

##### 8.5.1 Retryable Flow Test

- Inject failure in `DecisionService`
- Assert retry count

##### 8.5.2 Non-Retryable Flow Test

- Send malformed payload
- Assert direct DLT publish

##### 8.5.3 Retry Exhaustion Test

- Force retryable failure always
- Assert DLT after max attempts

##### 8.5.4 Header Propagation Test

- Consume DLT message
- Verify headers intact

##### 8.5.5 CI Validation

- Ensure tests stable in GitHub Actions
- No flakiness from retry timing

---

## ✅ Acceptance Criteria

| Criterion | Status |
|-----------|--------|
| Retryable failures are retried | DONE |
| Non-retryable failures are not retried | DONE |
| Poison messages end in DLT | DONE |
| Retry behavior configurable | DONE |
| Structured logs show failure lifecycle | DONE |
| All tests green locally and CI | DONE |

---

## 🚫 Out of Scope (Moved to Later Hardening)

The following items are **explicitly deferred** to advanced hardening phases:

- Circuit breaker
- Idempotency tokens
- Replay orchestration
- Ordering guarantees
- DLQ consumer logic

> **Note:** These belong to advanced hardening and will be addressed in future phases when operational needs justify the complexity.