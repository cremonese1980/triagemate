AAAP2 — Execution & Engineering Strategy

Goal
Turn AAAP1 into an AAA-ready, defensible engineering project while completing Phases 1–5 of the roadmap.

Execution Strategy

Build AAAP1 in parallel with roadmap phases

No rushing: concepts must be deeply understood

Every feature justified by a real use case

Engineering Standards

Java 21

Spring Boot 3.x

Clear package boundaries:

domain

application

infrastructure

Explicit ports/interfaces

No framework leakage into domain

Testing Strategy

Domain layer: pure unit tests (TDD mandatory)

Application layer: service-level tests

Infrastructure: minimal, focused tests

No UI tests in V1

Observability

Structured JSON logs

Correlation ID across services

Clear error classification

Git Discipline

Mono-repo (triagemate)

Two services inside repo

Small, intentional commits

Refactor commits clearly labeled

Timeline (Indicative)

V1 core (AAAP1): ~4–6 weeks

V1 hardened + demo: ~8 weeks total

V2 ideas (after V1):

Auto-reply agent

Multi-source ingest (ticket systems, WhatsApp, etc.)

Fine-tuning per customer

SaaS or API offering

Non-Goals

No premature scaling

No overengineering

No generic “AI platform” claims

Definition of Success

Project is:

understandable by a senior reviewer

demonstrable to a non-tech SME

defensible in job interviews

reusable as “software boomerang” for future products

_________________________
AAAP2 — Execution Plan (V1) for TriageMate
Objective

Deliver a demoable V1 that proves the full pipeline end-to-end:
Gmail → ingest → persist raw → enqueue job → triage service → LLM classify → persist result → expose API, with structured logs + requestId.

V1 Scope Freeze (what we ship)
Included

Real Gmail integration (polling first)

Canonical internal message model

Persistence:

raw messages (ingest)

triage results (triage)

Queue between services (simple + reliable)

LLM call via Spring AI / OpenAI

Classification output:

priority (e.g. URGENT/NORMAL/LOW)

category (e.g. BILLING/TECH/OTHER)

short rationale (1–2 lines)

Observability:

requestId propagation ingest → triage

JSON logs

minimal logging discipline rules

Demo scenario script + sample data

Excluded (explicitly)

Auto-reply agent

Ticket systems / WhatsApp

Fine-tuning

Multi-tenant SaaS

UI

Architecture (confirmed)

Two services, single-module each

triagemate-ingest (source adapters, normalization, raw storage, enqueue)

triagemate-triage (LLM classification, rules, result storage)

No Maven multi-module. Separation via packages.

Vertical Slice Plan (build in this exact order)
Slice 1 — “System boots + contract”

Done / already close

Both services start

One endpoint per service works (/api/info etc.)

RestClient between them (already from ms1/ms2)

Acceptance

curl to both services returns 200

health endpoints OK

Slice 2 — “Ingest message API + raw persistence”

Add to triagemate-ingest:

REST endpoint: POST /api/messages

Validate payload

Persist raw message (DB or file-based store temporarily)

Acceptance

POST saves raw message

GET raw messages returns what was saved

Slice 3 — “Queue + triage job dispatch”

Add:

enqueue a TriageJob after raw save

worker consumes jobs and calls triage service

Queue option for V1 (pick one):

In-memory for day 1 demo (fast)

then RabbitMQ for real reliability (recommended)

Acceptance

Posting a message triggers a job

job reaches triage service reliably

Slice 4 — “LLM classification in triage service”

Add to triagemate-triage:

endpoint/handler: classify message

integrate Spring AI + OpenAI

return TriageResult

Acceptance

Given a known input, returns valid JSON with priority/category/rationale

timeouts + error mapping defined

Slice 5 — “Persist triage result + query API”

Add:

store TriageResult

GET /api/triage-results with filters (optional)

Acceptance

after POST message, a triage result exists and is queryable

Slice 6 — “Observability close (Phase 4 completion)”

Implement:

JSON structured logs (logback encoder)

logging discipline rules:

INFO for normal milestones

WARN for recoverable issues (timeouts, retries)

ERROR only when request fails

requestId propagated across:

HTTP boundaries

queue message headers

Acceptance

One end-to-end run produces multiple log lines across both services

Same requestId everywhere

Clear root-cause visibility

Testing Strategy (V1)

Domain + application: unit tests (TDD)

Minimal integration test per service:

SpringBootTest + Testcontainers when DB/queue introduced

No UI tests

Deliverables (definition of “done V1”)

docker-compose up runs:

ingest

triage

dependencies (db/queue if used)

README:

run instructions

curl examples

architecture diagram (simple)

Demo script (3–5 minutes):

send message

show triage result

show correlated logs

V2 Hooks (kept in design, not built)

reply agent

fine-tuning per SME

additional sources (tickets/whatsapp)

multi-tenant SaaS