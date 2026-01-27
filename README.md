# Triagemate

Triagemate is a backend **decision-support system** designed to process incoming requests,
normalize them, apply business logic (and optionally AI), and produce **clear operational outputs**.

AI is treated as a **pluggable component**, not the core of the system.

The project is built as a realistic, reusable backend foundation suitable for:
- technical demos
- portfolio review
- backend / AI-oriented engineering interviews
- future custom, client-specific solutions

---

## Architecture overview

The system is composed of two independent services:

### triagemate-ingest
- Receives incoming requests over HTTP
- Normalizes and validates input
- Assigns correlation / request IDs
- Forwards data to downstream components

**Default port:** `8081`

---

### triagemate-triage
- Receives normalized input
- Applies decision logic (rules plus AI when enabled)
- Produces structured decision outputs:
    - priority
    - category
    - summary
    - operational suggestions

**Default port:** `8082`

---

## Running locally

Each service is a standalone Spring Boot application.

### Option 1 — Run from IDE (recommended)
- Import the project in your IDE
- Run:
    - `TriagemateIngestApplication`
    - `TriagemateTriageApplication`
- Activate the desired Spring profile (for example: `prod`)

### Option 2 — Run packaged jars
From each module directory:
java -jar target/<artifact-name>.jar


Environment-specific configuration is provided via **Spring profiles** and environment variables.

---

## Testing

Tests are executed per module.

From the module directory:
mvn test


Maven is used only for building and testing.  
Runtime execution does not rely on Maven.

---

## Documentation

All project documentation lives in the `docs/` directory.

Key documents:
- [`docs/git-workflow.md`](docs/git-workflow.md) — branching and merge rules
- [`docs/commit-messages.md`](docs/commit-messages.md) — commit message convention
- [`docs/logging.md`](docs/logging.md) — logging discipline and structure

---

## Logging and observability

- Logs are structured (JSON)
- Correlation and request IDs are propagated across services
- No secrets or sensitive data are logged

See [`docs/logging.md`](docs/logging.md) for detailed rules..yml

---

## Status

Current focus:
- backend correctness
- observability
- CI discipline
- event-driven evolution

By design, the project does **not** include:
- UI
- public SaaS features
- aggressive automation
