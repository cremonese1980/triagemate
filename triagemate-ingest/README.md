# triagemate-ingest

Ingest service for TriageMate.  
Its responsibility is to receive external inputs via HTTP and publish them as
versioned events to Kafka.

This service does **no decision-making** and **no execution**.  
It only normalizes inputs and emits events.

---

## Responsibilities

- Expose an HTTP endpoint to ingest messages
- Validate incoming requests
- Produce a versioned Kafka event (`input-received.v1`)
- Return deterministic HTTP responses
- Do **not** perform triage, AI, or business logic

---

## API

### POST `/api/ingest/messages`

**Headers:**
- `Content-Type: application/json`
- `X-Request-Id` (optional but recommended)

**Body example:**

```json
{
  "channel": "email",
  "content": "Customer reports login issue"
}
```

**Responses:**

- `202 Accepted` → message successfully published to Kafka
- `503 Service Unavailable` → temporary failure while publishing to Kafka

---

## Kafka

This service produces events to Kafka.

### Topic

```
triagemate.ingest.input-received.v1
```

### Event model

- Events are wrapped in a versioned `EventEnvelope`
- Payload is `InputReceivedV1`
- Contracts are defined in `triagemate-contracts`

---

## Running locally

### Prerequisites

- Java 21
- Kafka reachable from the service

You can run Kafka locally (e.g. Docker) and expose it on `localhost:9092`.

### Configuration

Set the Kafka bootstrap servers:

```bash
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

### Start the service

```bash
./mvnw spring-boot:run
```

---

## Testing

Run all tests with:

```bash
./mvnw test
```

**Notes:**
- Kafka integration tests use Testcontainers
- Kafka is started automatically in Docker during tests
- No external Kafka installation is required for testing

---

## Design notes

- Event-driven, decision-first architecture
- Kafka is treated as infrastructure, not business logic
- AI (if any) is handled downstream, not in this service
- Failures are mapped to explicit HTTP semantics
