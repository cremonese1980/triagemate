# 🔵 PHASE 11 — Observability & Operational Hardening

## 📊 STATE
```
Status:       NOT_STARTED
Phase:        11
Change ID:    TM-11
Stage:        A
Owner:        Gabriele
Branch:       feat/phase-11-observability
Depends On:   v0.10.0
Last Updated: 2026-02-24
Target Tag:   v0.11.0

Goal:
  - Full structured JSON logging
  - End-to-end correlation (requestId + correlationId + eventId)
  - Metrics discipline (business + infra)
  - Production-safe logging policy
  - Debuggability under failure

DoD Status: not_met
FRP Active: false
```

---

## 🅐 Design Freeze

### Problem

Il sistema ora è:
- idempotente
- transactional (outbox)

Ma non è ancora:
- audit-grade
- debug-grade
- operativamente osservabile

Serve:
- log strutturato coerente
- MDC propagation robusta
- metriche business
- disciplina logging (no log rumore)

---

## 🅑 Implementation Tasks

### 11.1 — Structured Logging Discipline

#### 11.1.a — Enforce JSON logging everywhere

- logback configurato solo JSON
- nessun log plain-text in prod-like

**Acceptance:**
- tutti i servizi loggano JSON coerente
- campi minimi: timestamp, level, service, trace fields

#### 11.1.b — Mandatory fields policy

Ogni log deve contenere:
- `requestId`
- `correlationId`
- `eventId` (se presente)
- `service`
- `decisionOutcome` (se applicabile)

**Acceptance:**
- nessun log business-critical senza trace fields

#### 11.1.c — Log level governance

Definire policy:

| Level | Uso |
|-------|-----|
| ERROR | solo errori reali |
| WARN | degradazioni |
| INFO | business transitions |
| DEBUG | solo diagnostica |

**Acceptance:**
- niente INFO rumorosi
- niente stacktrace inutili

---

### 11.2 — Correlation & MDC Hardening

#### 11.2.a — MDC population at Kafka boundary

Nel consumer:
- popolare MDC con requestId / correlationId / eventId
- clear MDC a fine processing

**Acceptance:**
- MDC sempre coerente
- no leakage tra thread

#### 11.2.b — Propagation into outbox publisher

Publisher deve:
- leggere payload
- ripristinare MDC prima del publish log

**Acceptance:**
- log publish correlato correttamente

#### 11.2.c — Thread-safety verification

- nessun MDC bleed tra parallel consumer thread
- test manuale con concorrenza

---

### 11.3 — Business Metrics

#### 11.3.a — Decision metrics

Timer già presente → estendere con:
- counter `decision_total`
- counter `decision_duplicate`
- counter `decision_invalid`

**Acceptance:**
- metriche esposte su `/actuator/prometheus`

#### 11.3.b — Outbox metrics

- counter `outbox_published`
- counter `outbox_retry`
- gauge `outbox_pending_count`

**Acceptance:**
- metriche aggiornate in tempo reale

#### 11.3.c — Failure metrics

- `kafka_publish_failure`
- `validation_failure`

**Acceptance:**
- ogni errore significativo incrementa una metrica

---

### 11.4 — Operational Safety

#### 11.4.a — Health indicators custom

- health indicator per outbox backlog
- health indicator per kafka connectivity

**Acceptance:**
- `/actuator/health` mostra subcomponents

#### 11.4.b — Backlog guardrail

Se outbox pending > soglia:
- log WARN
- health status degradato

#### 11.4.c — Graceful shutdown

Verificare:
- publisher interrompe loop
- no half-written updates

---

### 11.5 — Verification

#### 11.5.a — Load sanity test

Simulare:
- 100+ eventi

Verificare:
- no MDC bleed
- metriche coerenti
- outbox drained

#### 11.5.b — Failure injection test

- kill Kafka
- verificare retry
- verificare metriche failure

#### 11.5.c — Log audit review

Ispezione manuale:
- log leggibile
- tracciabilità completa
- nessun dato sensibile leakato

---

## 🅒 Done Criteria

**Phase 11 DONE quando:**

- ✅ JSON logging enforced
- ✅ MDC propagation robusta
- ✅ Metriche business + outbox attive
- ✅ Health custom indicator presente
- ✅ Backlog guardrail funzionante
- ✅ CI green
- ✅ Tag v0.11.0