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