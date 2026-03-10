# Phase 6 — Event-Driven Core (Kafka)

## Goal

Introduce an **event-driven backbone** using Kafka, keeping the system:
- minimal
- production-shaped
- demo- and interview-ready

The focus is **decision flow**, not infrastructure complexity.

---

## 6.1 Infrastructure (Docker + Kafka)

**Objective:** run Kafka locally with zero friction.

Tasks:
- Add `docker-compose.yml` at project root
- Services:
  - Kafka (single broker)
  - Zookeeper **or** KRaft (choose one, no cluster)
- Expose ports for local development
- Verify broker availability

Acceptance:
- `docker-compose up` starts Kafka
- Broker reachable from services

Output:
- `docker-compose.yml`
- README snippet to start Kafka

---

## 6.2 Event Contracts & Topics

**Objective:** define stable, explicit event contracts.

Tasks:
- Define JSON event envelopes:
  - `IngestEvent`
  - `DecisionEvent`
- Define topic naming convention:
  - `triagemate.ingest.events`
  - `triagemate.decision.events`
  - optional: `triagemate.deadletter.events`
- Decide and document:
  - message key strategy (e.g. `requestId`)
  - payload versioning field

Acceptance:
- Events are versioned
- Topics are named consistently

Output:
- `docs/eventing.md`

---

## 6.3 Ingest → Kafka (Producer)

**Objective:** publish ingest events.

Tasks:
- Add Spring Kafka dependency to ingest module
- Configure producer (bootstrap servers via env vars)
- Publish `IngestEvent` on request handling
- Ensure:
  - `correlationId` / `requestId` propagation
  - structured logs on publish success/failure

Acceptance:
- Ingest service produces Kafka messages
- Logs include correlationId

Output:
- Working Kafka producer in ingest module

---

## 6.4 Kafka → Triage (Consumer)

**Objective:** consume ingest events and emit decisions.

Tasks:
- Add Kafka consumer to triage module
- Consume `IngestEvent`
- Apply minimal decision logic
- Publish `DecisionEvent`
- Log decisions with correlationId

Acceptance:
- Triage reacts only to events
- No synchronous coupling to ingest

Output:
- Working Kafka consumer + producer in triage module

---

## 6.5 Error Handling (Minimal but Real)

**Objective:** handle failures explicitly.

Tasks:
- Define failure strategy:
  - retry (Kafka/Spring defaults)
  - dead-letter or parking-lot topic
- Ensure failures:
  - are logged
  - include correlationId
  - do not block the consumer loop

Acceptance:
- Broken messages are isolated
- Consumer keeps running

Output:
- DLQ topic
- Documented failure behavior

---

## 6.6 Integration Tests (Testcontainers)

**Objective:** prove the event flow end-to-end.

Tasks:
- Add Testcontainers Kafka
- Write one integration test:
  - send ingest request
  - assert `DecisionEvent` is produced
- Ensure test stability in CI

Acceptance:
- Test passes locally and in CI
- No reliance on external Kafka

Output:
- Kafka-backed integration test

---

## 6.7 Documentation & Positioning

**Objective:** make the architecture obvious to readers.

Tasks:
- Update `README.md`:
  - how to start Kafka
  - how services interact
- Finalize `docs/eventing.md`
- Explicitly state:
  - AI is a plug-in
  - Kafka is the decision backbone

Acceptance:
- Repo clearly reads as:
  **event-driven decision system**

Output:
- Clean, readable documentation

---

## Phase 6 Summary

- Kafka is the core decision backbone
- Services communicate via events
- AI is optional and replaceable
- Architecture is realistic, defendable, and extensible

This phase intentionally avoids:
- Kubernetes
- multi-broker Kafka
- SaaS hardening
- over-automation
