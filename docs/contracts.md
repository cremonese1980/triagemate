# Event Contracts & Conventions

This document defines the **rules, structure, and guarantees**
for all Kafka events in the Triagemate system.

It is mandatory reading before adding or modifying any event.

---

## 1. Topic Naming Convention

All topics follow this format:

```
<domain>.<event-name>.v<version>
```

### Examples

```
triagemate.ingest.message-ingested.v1
triagemate.triage.message-triaged.v1
```

### Rules

- Use **kebab-case** for event names
- Version starts at `v1`
- Version increases only for **breaking changes**
- Never reuse a topic name with different semantics

---

## 2. Event Envelope (Mandatory)

All events MUST be wrapped in a common envelope.

### Fields

| Field           | Type     | Description |
|-----------------|----------|-------------|
| `eventId`       | UUID     | Unique identifier for this event |
| `eventType`     | string   | Logical event name |
| `timestamp`     | ISO-8601 | Event creation time (UTC) |
| `requestId`     | string   | Correlation / trace identifier |
| `sourceService` | string   | Producing service name |
| `payload`       | object   | Event-specific payload |

### JSON Example

```json
{
  "eventId": "3f9c8c4b-3b91-4a89-9d1f-4b8f5f6c1b12",
  "eventType": "message-ingested",
  "timestamp": "2026-01-29T09:41:12.123Z",
  "requestId": "req-7c92c9e4",
  "sourceService": "triagemate-ingest",
  "payload": {
    "...": "see payload examples below"
  }
}
```

---

## 3. Ingest Event — message-ingested.v1

**Topic**

```
triagemate.ingest.message-ingested.v1
```

**Payload Fields**

| Field        | Type     | Description |
|--------------|----------|-------------|
| `messageId`  | UUID     | Internal message identifier |
| `channel`    | string   | Source channel (email, slack, api, etc.) |
| `content`    | string   | Raw message text |
| `receivedAt` | ISO-8601 | Time the message was received |

**Full Example**

```json
{
  "eventId": "c5f1b4c0-6e93-4ef2-bde6-1b6b9c6b77a1",
  "eventType": "message-ingested",
  "timestamp": "2026-01-29T09:45:00.000Z",
  "requestId": "req-91a2b7f3",
  "sourceService": "triagemate-ingest",
  "payload": {
    "messageId": "9d3d2c8e-5d2f-4c6f-9a3f-1e2b4c5d6e7f",
    "channel": "email",
    "content": "Customer reports login failure after password reset",
    "receivedAt": "2026-01-29T09:44:58.421Z"
  }
}
```

---

## 4. Triage Event — message-triaged.v1

**Topic**

```
triagemate.triage.message-triaged.v1
```

**Payload Fields**

| Field        | Type     | Description |
|--------------|----------|-------------|
| `messageId`  | UUID     | Original message identifier |
| `category`   | string   | Assigned category |
| `priority`   | string   | LOW / MEDIUM / HIGH |
| `confidence` | number   | Model confidence (0.0–1.0) |
| `triagedAt`  | ISO-8601 | Time triage completed |

**Full Example**

```json
{
  "eventId": "8b6e5c3a-0b1a-4c88-9bfa-6e92c8b7d999",
  "eventType": "message-triaged",
  "timestamp": "2026-01-29T09:46:10.000Z",
  "requestId": "req-91a2b7f3",
  "sourceService": "triagemate-triage",
  "payload": {
    "messageId": "9d3d2c8e-5d2f-4c6f-9a3f-1e2b4c5d6e7f",
    "category": "AUTHENTICATION",
    "priority": "HIGH",
    "confidence": 0.87,
    "triagedAt": "2026-01-29T09:46:08.912Z"
  }
}
```

---

## 5. Compatibility Rules (Strict)

**Allowed (Backward Compatible)**

- Add new optional fields
- Add new event types
- Add new topic versions

**Forbidden (Breaking)**

- Removing fields
- Renaming fields
- Changing field meaning
- Changing data types

Breaking changes require a new topic version.

---

## 6. Correlation & Observability

- `requestId` MUST be propagated unchanged across services
- It MUST match the HTTP `X-Request-Id` when present
- Logs must include the same `requestId` for traceability

---

## 7. Final Rule

> If a consumer breaks because of your change,  
> you versioned incorrectly.

Contracts are APIs.  
Treat them with the same discipline.