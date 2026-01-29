# Triagemate Contracts

This module defines **shared, versioned event contracts** used for asynchronous communication
between Triagemate services via Kafka.

It contains **no business logic**.
Its only responsibility is to provide **stable schemas and conventions** that all services can depend on.

## Purpose

- Prevent schema drift between producers and consumers
- Make events self-describing and evolvable
- Enable safe refactoring and backward compatibility
- Serve as a single source of truth for Kafka payloads

## Module Characteristics

- Plain Java module (JAR)
- No Spring dependencies
- No Kafka client dependencies
- Imported by:
    - `triagemate-ingest`
    - `triagemate-triage`
    - Any future consumer/producer

## Package Structure

```
triagemate-contracts
└── com.triagemate.contracts
    ├── envelope
    │   └── EventEnvelope.java
    ├── ingest
    │   └── MessageIngestedEvent.java
    ├── triage
    │   └── MessageTriagedEvent.java
    └── common
        └── EventType.java
```

## Event Transport

- Transport: **Kafka**
- Serialization: **JSON**
- Schema registry: **not used (by design)**
- Compatibility is enforced by **conventions and discipline**, not tooling

## Versioning Strategy

- Topics are **versioned**
- Event payloads are **append-only**
- Breaking changes require a **new topic version**

Example:

```
triagemate.ingest.message-ingested.v1
triagemate.ingest.message-ingested.v2
```

## What Belongs Here (and What Does Not)

### ✅ Allowed
- Event records (DTOs)
- Event envelope
- Enums
- Documentation

### ❌ Forbidden
- Spring annotations
- Kafka producers/consumers
- Business logic
- Validation logic beyond basic structural constraints

## How to Change a Contract Safely

1. Add new optional fields (non-breaking)
2. Never rename or remove existing fields in the same version
3. If semantics change → create a new event + new topic version
4. Update `docs/contracts.md`

Breaking changes without versioning are **not allowed**.

---

This module is intentionally boring.  
Boring contracts are reliable contracts.
