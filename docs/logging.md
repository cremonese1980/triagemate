# Logging (Triagemate)

## Goals
Our logging strategy is designed to support production-grade operations:

- **Trace requests end-to-end** across services using a `requestId`
- **Structured logs** (JSON) for ingestion by log platforms (ELK, Loki, Datadog, etc.)
- **Safe-by-default**: avoid leaking secrets, PII, or sensitive payloads
- **Actionable**: logs should explain what happened and where, without noise

---

## Formats by profile
### `prod`
- **JSON logs** on stdout (container-friendly)
- Includes:
    - `@timestamp`, `level`, `logger_name`, `thread_name`, `message`
    - `service` (application name)
    - `requestId` from MDC (when request-scoped)

### `local` / `dev`
- Human-readable console logs
- Shows `requestId` inline in brackets

---

## Correlation: `requestId`
### Header
- Incoming header: `X-Request-Id`
- If missing, generated at the edge (ingest entrypoint).

### MDC
- We store it in MDC as: `requestId`
- It is added to every log line produced within the request scope.

### Propagation
- When service A calls service B, A forwards the `X-Request-Id` header.
- Result: one user request produces consistent correlation across services.

---

## What we log
### INFO
Use for business-relevant events and lifecycle steps:
- Service start/stop (Spring Boot)
- Entry/exit of key endpoints (one line per request)
- High-level decisions (e.g., chosen route, strict vs best-effort mode)
- External calls made (destination, method, outcome)
- Latency summaries (duration, success/failure)

Recommended fields (as structured info or message content):
- `service`
- `requestId`
- operation name (endpoint or use-case)
- status (success/fail)
- latency (ms)

### WARN
Use for recoverable issues:
- Downstream temporary failures when best-effort continues
- Retries (if introduced later)
- Invalid input that is handled gracefully

### ERROR
Use for non-recoverable failures or strict-mode failures:
- Downstream timeouts/unavailable that abort the request
- Unexpected exceptions (bug-class issues)

Include:
- exception type + message
- safe context (endpoint, downstream name, status code)
  Avoid including raw payloads.

---

## What we NEVER log
- Secrets: API keys, tokens, credentials, cookies
- Authorization headers
- Full request/response bodies from users
- Full LLM prompts/responses (only summaries + ids if needed)
- Personal data (PII): emails, phone numbers, addresses, IDs
- Payment data or anything regulated

If you need troubleshooting data:
- log **counts**, **hashes**, **lengths**, **ids**, or **redacted** snippets (max ~50 chars)
- prefer DEBUG locally rather than INFO/ERROR in prod

---

## Logging discipline rules
- **One log line per request** at the controller boundary (INFO), plus one for downstream call result if relevant.
- Keep messages short and consistent (machine-friendly).
