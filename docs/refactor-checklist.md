efactor Checklist — TriageMate (confirmed version)
Scope

Refactor existing ms1 and ms2 into the TriageMate mono-repo, keeping:

2 microservices

single-module each

clear package separation (DDD-style, not Maven multi-module)

1. Repository Structure (GitHub root)
   triagemate/
   ├── triagemate-ingest/
   ├── triagemate-triage/
   ├── docs/
   │   ├── ms-roadmap.md
   │   ├── refactor-checklist.md   <-- THIS FILE
   │   └── aaap/
   │       ├── AAAP1.md
   │       └── AAAP2.md
   └── README.md


👉 Checklist file location
docs/refactor-checklist.md

This becomes the single source of truth for refactor decisions.

2. Microservice: triagemate-ingest
   Responsibility

Ingest messages from external sources (Gmail now, others later)

Normalize input into a canonical IncomingMessage

Persist raw messages

Enqueue triage jobs

Never contain AI logic

Package structure
com.gabriele.triagemate.ingest
├── api            // REST endpoints, webhooks
├── application    // use cases, orchestration
├── domain         // core domain model (Message, Source, Metadata)
├── infrastructure // DB, queues, external clients
├── adapters
│   └── gmail      // Gmail-specific integration
└── config

3. Microservice: triagemate-triage
   Responsibility

Consume triage jobs

Call LLMs (Spring AI / OpenAI)

Classify, prioritize, enrich messages

Produce structured decisions (priority, category, action)

Package structure
com.gabriele.triagemate.triage
├── api            // internal APIs if needed
├── application    // triage pipelines, workflows
├── domain         // TriageResult, Priority, Category
├── infrastructure // LLM clients, persistence
├── adapters       // OpenAI, Spring AI
└── config

4. Cross-cutting rules (VERY IMPORTANT)

❌ No shared code module yet

❌ No Maven multi-module

❌ No CRM / Helpdesk logic

❌ No UI

❌ No auto-reply agent in V1

✅ Structured logging (JSON)

✅ Correlation ID propagated ingest → triage

✅ Clear error classification

✅ Tests on domain + application layers

✅ Ports & adapters inside packages, not Maven modules

5. Git & Refactor Procedure

Move ms1 → triagemate-ingest

Move ms2 → triagemate-triage

Fix:

artifactId

Spring application name

base package names

Verify:

both services start

inter-service communication still works

Commit from repo root:

git status
git add .
git commit -m "Refactor ms1/ms2 into TriageMate ingest/triage services"
git push

6. IDE Setup (recommended)

✅ One IntelliJ project opened on triagemate/ root

Each microservice imported as a separate Spring Boot run config

This mirrors real-world mono-repo workflows

Why this checklist matters

It enforces architectural discipline

It prevents scope creep

It is interview-defensible

It keeps Phase 5 (refactor) meaningful

It prepares AAAP2 cleanly

If you want, next step tomorrow:

AAAP2 surgical definition

or first vertical slice in triagemate-ingest (Gmail → DB → queue)