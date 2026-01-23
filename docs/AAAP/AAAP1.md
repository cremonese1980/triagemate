AAAP1 — TriageMate (Core Product Definition)

Goal
Build a real, sellable AI-driven triage system for SMEs that automatically classifies and prioritizes incoming messages (email first, extensible to other sources).

Problem Solved
SMEs receive messages from many channels and lack time/process to:

identify urgent vs non-urgent messages

route messages correctly

respond consistently

TriageMate reduces response time and human load.

Scope V1 (STRICT)
What V1 does:

Ingest messages from real Gmail integration

Normalize messages into a common internal model

Classify messages using LLMs (intent, priority, category)

Assign tags and priority (e.g. URGENT / NORMAL / LOW)

Persist results

Expose results via REST API

Full observability (structured logs, correlation ID)

What V1 does NOT:

No automatic replies

No full CRM/helpdesk

No perfect NLP accuracy claims

No fine-tuning (but architecture must allow it later)

Architecture (V1)
Two microservices, single-module each:

triagemate-ingest

Owns source adapters (Gmail)

Message normalization

Persistence of raw messages

Enqueues triage requests

triagemate-triage

Core triage logic

LLM integration (Spring AI + OpenAI)

Classification rules

Stores triage outcome

Design Principles

Hexagonal architecture (ports & adapters)

Core logic unaware of message source

Clean domain model

TDD on domain and application layers

Deliverables

GitHub repo

README (product + tech)

Demo video (3–5 min)

Runnable locally via Docker Compose

Target Users

SMEs (primary)

Internal support teams (secondary)