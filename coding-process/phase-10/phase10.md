# 🔵 PHASE 10 — Transactional Outbox & Exactly-Once Discipline

## 📊 STATE

```yaml
Status:       NOT_STARTED
Phase:        10
Change ID:    TM-10
Stage:        A  # A=Design | B=Change Design | C=Implementation | D=Verification | E=Done
Owner:        Gabriele
Branch:       feat/phase-10-outbox-pattern
Depends On:   v0.9.2
Last Updated: 2026-02-24
Target Tag:   v0.10.0

Goal:
  - Eliminate dual-write risk (DB commit + Kafka publish)
  - Introduce transactional outbox pattern
  - Enable replayable, restart-safe publication
  - Prepare for horizontal scaling

Applied Changes:
  - none

Stabilized Changes:
  - none

Pending Verification:
  - outbox persistence + publish loop
  - correctness under Kafka failures
  - no direct Kafka publish inside business transaction

Tests Status:
  unit:        pending
  integration: pending
  manual:      pending

DoD Status:           not_met
Last Green Commit:    v0.9.2
FRP Active:           false
```

**Phase Lineage:**
```
v0.9.2 (Durable idempotency) → Phase 10 (Outbox) → v0.10.0
```

---

## 🅐 Design Freeze

### Problem Statement

Con il flow attuale, anche con `@Transactional`, esiste il **dual-write hazard**:

1. DB commit ✅
2. Kafka publish ❌ (network/broker/timeout)
3. Stato inconsistente → perdita evento o emissione non tracciata

Serve rendere **DB l'unica source of truth** per "cosa deve essere pubblicato".

### Solution (Outbox Pattern)

**Dentro la transazione:**
- scrivo `processed_events` (idempotency)
- scrivo `outbox_events` (da pubblicare)

**Fuori transazione (async):**
- un publisher legge `outbox_events(PENDING)` e pubblica su Kafka
- marca `SENT` (o lascia `PENDING` per retry)

### Explicit Scope

| In Scope ✅ | Out of Scope ❌ |
|-------------|-----------------|
| Schema `outbox_events` | CDC/Debezium |
| Outbox writer (persist) | Kafka transactions API |
| Polling publisher | DLQ/Retry advanced policy |
| Restart safety + replay | Observability dashboards |
| Integration tests base | Performance tuning |

### Non-negotiable rule (ban)

**Dopo Phase 10 è vietato:**

Qualsiasi `kafkaTemplate.send(...)` dentro la stessa `@Transactional` che fa write DB "business".

---

## 🅑 Implementation Tasks (macro)

### 10.1 — DB schema & JPA model (Outbox)

#### 10.1.a — Define table schema

**Deliverable:** DDL (o migration, se già introdotta) per `outbox_events`.

**Campi minimi (production-grade, ma semplici):**

- `id` (UUID, PK)
- `aggregate_type` (es: Decision)
- `aggregate_id` (es: decisionId)
- `event_type` (es: DecisionMade)
- `payload` (JSON string / JSONB)
- `created_at`
- `published_at` (nullable)
- `status` (PENDING, SENT, FAILED future)
- `retry_count` (int, default 0)
- `last_error` (text, nullable)

**Indici minimi:**
- `(status, created_at)`

**Acceptance:**
- tabella creata correttamente in docker/test
- indice presente
- status come enum string o varchar con valori limitati

---

#### 10.1.b — JPA Entity OutboxEvent

**Deliverable:** entity JPA con mapping coerente (no Lombok obbligatorio).

**Acceptance:**
- persist/merge ok
- payload non nullo
- status persistito come stringa

---

#### 10.1.c — Repository OutboxEventRepository

**Deliverable:** Spring Data repository con query per pending:

```java
findTopNByStatusOrderByCreatedAtAsc(...)  // (o equivalente)
```

**Acceptance:**
- recupero deterministico oldest-first
- limit/size controllabile

---

### 10.2 — Outbox writer (store event transactionally)

#### 10.2.a — OutboxService / OutboxWriter

**Deliverable:** service che, dato un "domain event" (DecisionMade), crea record outbox PENDING.

**Acceptance:**
- serializzazione payload deterministica
- aggregate_id valorizzato
- created_at valorizzato

---

#### 10.2.b — Replace direct routing in triage flow

**Deliverable:** nel flow consumer triage:
- al posto di `decisionRouter.route(...)` dentro transazione
- fai `outboxWriter.storeDecisionMade(...)`

**Acceptance:**
- nessuna publish Kafka nel transaction boundary "business"
- la decision "da pubblicare" è ora persistita

---

#### 10.2.c — Atomicity with idempotency

**Deliverable:** stessa transazione salva:
- `processed_events` (idempotency marker)
- `outbox_events` (to-be-published)

**Acceptance:**
- se fallisce uno dei due write → rollback completo
- nessun record outbox "orfano" senza processed marker (o viceversa)

---

### 10.3 — Publisher (poll + publish + mark SENT)

#### 10.3.a — Scheduled publisher

**Deliverable:** componente schedulato che:
1. legge batch di PENDING
2. pubblica su Kafka
3. aggiorna a SENT + published_at

**Acceptance:**
- batch size configurabile
- ordine deterministico (created_at asc)
- log a livello DEBUG/INFO controllato

---

#### 10.3.b — Error handling (minimal retry)

**Deliverable:** su failure publish:
- incrementa `retry_count`
- salva `last_error` (troncato/safe)
- lascia PENDING (per retry)
- opzionale: se `retry_count > max` → FAILED (se vuoi già ora)

**Acceptance:**
- nessun crash del publisher loop
- retry automatico su ciclo successivo

---

#### 10.3.c — Publish idempotency

**Deliverable:** key coerente (es `aggregate_id`) per minimizzare duplicates downstream.

**Acceptance:**
- publish usa key stabile
- consumer downstream resta idempotente (già coperto da Phase 9.2)

---

### 10.4 — Failure semantics & recovery playbooks

#### 10.4.a — Kafka down scenario

**Deliverable:** comportamento definito:
- outbox resta PENDING
- al ritorno di Kafka, il publisher recupera e invia

---

#### 10.4.b — App crash scenario

**Deliverable:** restart-safe:
- PENDING rimane in DB
- publisher riparte e invia

---

#### 10.4.c — Duplicate publish scenario

**Deliverable:** se evento viene pubblicato due volte (es: crash dopo send prima di mark):
- consumer idempotency (`processed_events`) protegge
- documentare chiaramente questo tradeoff

---

### 10.5 — Verification & tests

#### 10.5.a — Integration test: happy path

- input event → `processed_events` row
- outbox row PENDING → diventa SENT
- decision event presente in output topic

---

#### 10.5.b — Integration test: Kafka failure then recovery

- simula broker down
- outbox resta PENDING
- broker up → outbox diventa SENT

---

#### 10.5.c — Integration test: restart persistence

- crea outbox PENDING
- restart triage
- outbox pubblicata

**Acceptance (10.5):**
- CI green
- no flaky (timeout sensati)
- assert su DB + Kafka

---

### 10.6 — Docs & ADR

#### 10.6.a — ADR-006: Outbox Pattern

Decisione: polling vs CDC, rationale, failure modes.

---

#### 10.6.b — README update

Sezione "Outbox + exactly-once discipline" + troubleshooting.

---

#### 10.6.c — Update phase roadmap docs

Aggiornare file fase/indice se necessario.

---

## 🅒 Done Criteria (Phase 10 DONE quando)

- ✅ `outbox_events` schema presente + indici
- ✅ writer: outbox row creato atomicamente con idempotency marker
- ✅ publisher: PENDING → SENT, retry minimale su errori
- ✅ nessun Kafka publish dentro business transaction
- ✅ test integrazione principali verdi (happy, kafka down, restart)
- ✅ ADR-006 + README aggiornati
- ✅ tag `v0.10.0`

---

**Document Version:** 1.0  
**Last Updated:** 2026-02-24  
**Status:** Design phase — ready for implementation  
**Next Action:** Create ADR-006, then implement 10.1.a schema