# 🔵 PHASE 12 — Horizontal Scalability & Concurrency Control

## 📊 STATE

```yaml
Status:       NOT_STARTED
Phase:        12
Change ID:    TM-12
Stage:        A  # A=Design | B=Change Design | C=Implementation | D=Verification | E=Done
Owner:        Gabriele
Branch:       feat/phase-12-horizontal-scale
Depends On:   v0.11.0
Last Updated: 2026-02-24
Target Tag:   v0.12.0

Goal:
  - Safe horizontal scaling of triage service
  - Concurrency correctness under multiple instances
  - Deterministic partition processing
  - Backpressure & load control
  - Operational stability under scale

Applied Changes:
  - none

Stabilized Changes:
  - none

Pending Verification:
  - multi-instance consumer correctness
  - DB race condition handling
  - outbox concurrent publish safety
  - rebalance behavior
  - spike load handling

Tests Status:
  unit:        pending
  integration: pending
  manual:      pending

DoD Status:           not_met
Last Green Commit:    v0.11.0
FRP Active:           false
```

**Phase Lineage:**
```
v0.11.0 (Circuit Breakers) → Phase 12 (Horizontal Scale) → v0.12.0
```

---

## 🅐 Design Freeze

### Problem Statement

Finora hai testato:
- **1 istanza triage**
- **1 istanza ingest**
- **1 consumer group**

Ma production reale implica:
- **N istanze triage** — scale elastico
- **Partition rebalancing** — Kafka coordinator
- **Race su DB** — concurrent writes
- **Spike di eventi** — burst traffic
- **Backlog outbox** — delayed publishing

**Phase 12 rende il sistema scale-safe, non solo "funzionante".**

### Solution: Multi-Instance Correctness Discipline

**Garantire che il sistema funzioni correttamente con:**
1. Multiple consumer instances (partition assignment)
2. Concurrent DB writes (idempotency constraint protection)
3. Concurrent outbox publishing (atomic updates)
4. Rebalancing events (no lost messages)
5. Load spikes (backpressure management)

### Explicit Scope

| In Scope ✅ | Out of Scope ❌ |
|-------------|-----------------|
| Kafka consumer concurrency config | Auto-scaling policies |
| Partition rebalancing tests | Kubernetes orchestration |
| DB race condition verification | Distributed transactions |
| Outbox concurrent publish safety | Advanced sharding |
| Backpressure tuning | Performance benchmarking |
| Multi-instance Docker simulation | Production monitoring setup |
| Spike load testing | Load balancer configuration |

### Non-negotiable Requirements

**After Phase 12:**
- System MUST work correctly with 2+ instances
- Idempotency MUST protect from concurrent duplicate processing
- Outbox MUST handle concurrent publishers without double-publish
- Rebalancing MUST NOT cause data loss or duplicate side-effects

---

## 🅑 Implementation Tasks

### 12.1 — Kafka Consumer Concurrency Discipline

#### 12.1.a — Explicit concurrency config

**Deliverable:** Definire nel profilo docker:
- `concurrency` per `@KafkaListener`
- Numero partizioni coerente con istanze

**Configuration example:**

```yaml
# application-docker.yml
spring:
  kafka:
    listener:
      concurrency: 3  # Match topic partition count
    consumer:
      max-poll-records: 100
      fetch-min-size: 1024
      max-poll-interval-ms: 300000
```

**Acceptance:**
- Configurazione documentata in `application-docker.yml`
- No over-parallelism (concurrency <= partitions)
- Clear rationale in comments

---

#### 12.1.b — Partition ownership validation

**Deliverable:** Verificare:
- Ogni partition processata da un solo consumer instance
- Logging chiaro su assign/revoke

**Implementation:**

```java
@KafkaListener(
    topics = "triagemate.ingest.input-received.v1",
    groupId = "triagemate-triage"
)
public class TriageKafkaListener {

    @EventListener
    public void onPartitionsAssigned(ConsumerPartitionsAssignedEvent event) {
        log.info("Partitions ASSIGNED: {}", event.getPartitions());
    }

    @EventListener
    public void onPartitionsRevoked(ConsumerPartitionsRevokedEvent event) {
        log.info("Partitions REVOKED: {}", event.getPartitions());
    }
}
```

**Acceptance:**
- Log espliciti di rebalancing
- Nessun doppio consumo (verified in tests)
- Partition ownership clear in logs

---

#### 12.1.c — Rebalance safety test

**Deliverable:** Simulare:
- Scale up (2 istanze triage)
- Scale down

**Test scenario:**

```java
@Test
void rebalancing_doesNotCauseDataLoss() {
    // Given: 1 instance processing events
    startTriageInstance(1);
    produceEvents(100);
    
    // When: Scale up to 2 instances mid-processing
    startTriageInstance(2);
    
    // Then: All events processed exactly once
    await().atMost(30.seconds).untilAsserted(() -> {
        assertThat(processedEventsCount()).isEqualTo(100);
        assertThat(duplicateCount()).isEqualTo(0);
    });
    
    // When: Scale down to 1 instance
    stopTriageInstance(2);
    
    // Then: Processing continues without loss
    produceEvents(50);
    await().atMost(20.seconds).untilAsserted(() -> {
        assertThat(processedEventsCount()).isEqualTo(150);
    });
}
```

**Acceptance:**
- Nessun evento perso
- Idempotency + outbox proteggono da duplicate publish
- Rebalancing completes within reasonable time

---

### 12.2 — DB Concurrency & Locking Strategy

#### 12.2.a — Verify unique constraint race behavior

**Deliverable:** Test:
- 2 consumer processano stesso `eventId` simultaneamente

**Test scenario:**

```java
@Test
void concurrentDuplicateEvent_oneSucceeds_oneFailsGracefully() {
    // Given: Same event sent to 2 different partitions
    InputEvent event = createValidEvent("evt-race-001");
    
    CompletableFuture<Void> consumer1 = CompletableFuture.runAsync(() -> 
        processEvent(event)
    );
    CompletableFuture<Void> consumer2 = CompletableFuture.runAsync(() -> 
        processEvent(event)
    );
    
    // When: Both try to process simultaneously
    CompletableFuture.allOf(consumer1, consumer2).join();
    
    // Then: Exactly 1 processed_events row
    assertThat(processedEventsRepository.count()).isEqualTo(1);
    
    // And: Exactly 1 decision event published
    assertThat(decisionsPublished()).isEqualTo(1);
    
    // And: One consumer got DataIntegrityViolationException (logged, not crashed)
    assertThat(logContains("Duplicate event detected")).isTrue();
}
```

**Acceptance:**
- 1 insert ok
- 1 `DataIntegrityViolationException` (caught and logged)
- Nessun side-effect duplicato (outbox row count = 1)

---

#### 12.2.b — Transaction isolation review

**Deliverable:** Verificare livello di isolation default (PostgreSQL):
- `READ COMMITTED` (PostgreSQL default)
- No phantom harmful

**Documentation:**

```markdown
## Transaction Isolation Level

**PostgreSQL Default:** `READ COMMITTED`

**Implications:**
- Dirty reads: NOT possible ✅
- Non-repeatable reads: Possible (acceptable for our use case)
- Phantom reads: Possible (acceptable for our use case)

**Why this is safe:**
- Idempotency enforced by unique constraint (atomic)
- Outbox publishing uses row-level locking (SELECT FOR UPDATE SKIP LOCKED)
- No complex multi-row invariants to protect

**No escalation to SERIALIZABLE required.**
```

**Acceptance:**
- Comportamento documentato in `docs/concurrency.md`
- Nessuna escalation necessaria
- Rationale clear

---

#### 12.2.c — Outbox concurrent publish safety

**Deliverable:** Con 2 publisher instances:

**Implementation using row-level locking:**

```java
@Scheduled(fixedDelay = 200)
@Transactional
public void publishPendingEvents() {
    // Fetch PENDING events with row-level lock
    List<OutboxEvent> pending = entityManager.createQuery(
        "SELECT e FROM OutboxEvent e " +
        "WHERE e.status = :status " +
        "ORDER BY e.createdAt ASC",
        OutboxEvent.class
    )
    .setParameter("status", OutboxStatus.PENDING)
    .setMaxResults(batchSize)
    .setLockMode(LockModeType.PESSIMISTIC_WRITE)  // SELECT FOR UPDATE
    .setHint("javax.persistence.lock.timeout", 0)  // SKIP LOCKED
    .getResultList();
    
    for (OutboxEvent event : pending) {
        publishEvent(event);
        markAsSent(event);
    }
}
```

**Acceptance:**
- Nessun doppio publish per stessa row
- Update SENT atomic
- `SELECT FOR UPDATE SKIP LOCKED` prevents lock contention

---

### 12.3 — Backpressure & Flow Control

#### 12.3.a — Kafka poll batch tuning

**Deliverable:** Configurare:
- `max.poll.records`
- `fetch.min.bytes`
- `max.poll.interval.ms`

```yaml
# application-docker.yml
spring:
  kafka:
    consumer:
      max-poll-records: 100        # Batch size per poll
      fetch-min-size: 1024          # Wait for at least 1KB
      fetch-max-wait: 500           # Max wait 500ms
      max-poll-interval-ms: 300000  # 5 minutes max processing time
```

**Acceptance:**
- Configurazioni esplicite nel docker profile
- Batch size prevents overwhelming DB
- Poll interval allows time for processing

---

#### 12.3.b — Outbox batch tuning

**Deliverable:** Configurare:
- Batch size publisher
- Delay polling interval

```yaml
# application-docker.yml
triagemate:
  outbox:
    publisher:
      batch-size: 50
      fixed-delay-ms: 200
      max-retry-count: 10
```

**Acceptance:**
- No starvation (events published within reasonable time)
- No CPU busy loop (200ms delay)
- Batch size balances throughput vs latency

---

#### 12.3.c — Spike scenario test

**Deliverable:** Simulare:
- 1000+ eventi in breve tempo

**Test scenario:**

```java
@Test
void spikeLoad_1000Events_processedWithoutCrash() {
    // Given: System at steady state
    startTriageInstance(1);
    
    // When: Burst of 1000 events in 10 seconds
    for (int i = 0; i < 1000; i++) {
        kafkaTemplate.send(
            "triagemate.ingest.input-received.v1",
            createValidEvent("evt-spike-" + i)
        );
    }
    
    // Then: All events eventually processed
    await().atMost(2.minutes).untilAsserted(() -> {
        assertThat(processedEventsCount()).isEqualTo(1000);
    });
    
    // And: No crashes, no OOM
    assertThat(instanceHealthy()).isTrue();
    
    // And: Outbox drained
    assertThat(outboxPendingCount()).isEqualTo(0);
}
```

**Acceptance:**
- No crash
- No OOM (heap stays within limits)
- Backlog drenato progressivamente (not instantly)

---

### 12.4 — Horizontal Scale Simulation

#### 12.4.a — Multi-instance docker simulation

**Deliverable:** In `docker-compose.yml`:
- 2 triage replicas (temporaneo)

```yaml
services:
  triagemate-triage-1:
    image: triagemate-triage:latest
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - INSTANCE_ID=triage-1
    depends_on:
      - kafka
      - postgres

  triagemate-triage-2:
    image: triagemate-triage:latest
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - INSTANCE_ID=triage-2
    depends_on:
      - kafka
      - postgres
```

**Acceptance:**
- Consumer group stabilizzato
- Nessun double-processing effettivo
- Logs show partition assignment per instance

---

#### 12.4.b — Kill one instance mid-flight

**Deliverable:** Simulare:
- `kill -9` container triage

**Test scenario:**

```bash
# Start 2 instances
docker compose up -d triagemate-triage-1 triagemate-triage-2

# Produce events
for i in {1..100}; do
  kafka-console-producer --topic triagemate.ingest.input-received.v1 \
    --bootstrap-server localhost:9092 < event-$i.json
done

# Kill instance mid-processing
docker compose kill -s SIGKILL triagemate-triage-1

# Wait for rebalance
sleep 10

# Verify all events processed
psql -U triagemate_dev -d triagemate -c \
  "SELECT COUNT(*) FROM processed_events;"
# Expected: 100
```

**Acceptance:**
- Rebalance completes
- Eventi riprocessati correttamente (idempotency prevents duplicates)
- No duplicate side effects (outbox count correct)

---

#### 12.4.c — Restart safety full cycle

**Deliverable:** Test:
- Stop entire stack
- Restart
- Backlog outbox drained

**Test scenario:**

```bash
# 1. Produce events with Kafka down (outbox accumulates)
docker compose stop kafka
# Events accumulate in outbox as PENDING

# 2. Stop entire stack
docker compose down

# 3. Restart everything
docker compose up -d

# 4. Verify outbox drained
sleep 30
psql -U triagemate_dev -d triagemate -c \
  "SELECT COUNT(*) FROM outbox_events WHERE status = 'PENDING';"
# Expected: 0
```

**Acceptance:**
- Consistency preserved
- All PENDING events published after restart
- No data loss

---

### 12.5 — Resource Hardening

#### 12.5.a — JVM tuning baseline

**Deliverable:** Configurare:
- Memory limits container
- GC logging (debug only)

```yaml
# docker-compose.yml
services:
  triagemate-triage:
    environment:
      - JAVA_OPTS=-Xmx512m -Xms256m -XX:+UseG1GC -XX:+PrintGCDetails
    deploy:
      resources:
        limits:
          memory: 768M
        reservations:
          memory: 256M
```

**Acceptance:**
- No OOM during spike test
- Memory predictable (heap stays within limits)
- GC logs available for debugging

---

#### 12.5.b — Connection pool sizing

**Deliverable:** Allineare:
- Hikari pool size
- Kafka concurrency

```yaml
# application-docker.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10      # Kafka concurrency (3) + outbox publisher (1) + buffer
      minimum-idle: 3
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

  kafka:
    listener:
      concurrency: 3
```

**Formula:**
```
Pool Size = (Kafka Concurrency × Avg Connections per Request) + Buffer
          = (3 × 2) + 4
          = 10
```

**Acceptance:**
- No pool exhaustion
- No deadlock
- Connection usage monitored

---

#### 12.5.c — Graceful shutdown validation

**Deliverable:** Verificare:
- No half transaction
- No partial publish

**Implementation:**

```yaml
# application-docker.yml
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s

server:
  shutdown: graceful
```

**Test scenario:**

```bash
# Start processing events
docker compose up -d triagemate-triage

# Send SIGTERM (graceful shutdown)
docker compose stop triagemate-triage

# Verify no partial state
psql -U triagemate_dev -d triagemate -c \
  "SELECT COUNT(*) FROM processed_events WHERE decision_id IS NULL;"
# Expected: 0 (no orphaned idempotency records)
```

**Acceptance:**
- Listeners stop accepting new messages
- In-flight transactions complete
- Outbox publisher finishes current batch
- Clean shutdown (no partial state)

---

## 🅒 Verification Matrix

### Integration Tests

| Test | Scenario | Expected Result |
|------|----------|-----------------|
| **12.1.c** | Rebalance safety | No data loss, no duplicates |
| **12.2.a** | Concurrent duplicate | 1 success, 1 graceful failure |
| **12.2.c** | Concurrent outbox publish | No double-publish |
| **12.3.c** | Spike 1000 events | All processed, no crash |
| **12.4.a** | 2 instances steady state | Correct partition assignment |
| **12.4.b** | Kill instance mid-flight | Rebalance + recovery |
| **12.4.c** | Full restart cycle | Outbox drained, consistency ok |
| **12.5.c** | Graceful shutdown | No partial transactions |

---

### Manual Verification Checklist

| Step | Command | Expected Result |
|------|---------|-----------------|
| 1 | `docker compose up -d triagemate-triage-1 triagemate-triage-2` | 2 instances start, join consumer group |
| 2 | Check logs for partition assignment | Each instance assigned different partitions |
| 3 | Produce 100 events | All processed, no duplicates |
| 4 | `docker compose kill triagemate-triage-1` | Rebalance, instance 2 takes over |
| 5 | Check `processed_events` count | Still 100 (no loss) |
| 6 | Check `outbox_events` SENT count | 100 (no double-publish) |
| 7 | Produce 1000 events rapidly | All processed within 2 minutes |
| 8 | Check heap usage | Stays under 512MB |
| 9 | `docker compose stop` | Graceful shutdown, no errors |
| 10 | `docker compose up -d` | Restart, pending outbox drained |

---

## 🅓 Done Criteria

Phase 12 is **DONE** when:

- ✅ 2 istanze triage funzionano correttamente
- ✅ Idempotency protegge da race (concurrent duplicate test green)
- ✅ Outbox safe under concurrency (`SELECT FOR UPDATE SKIP LOCKED`)
- ✅ Rebalance tested (scale up/down without data loss)
- ✅ Spike test superato (1000 events processed, no crash)
- ✅ Resource limits configured (memory, pool size)
- ✅ Graceful shutdown validated
- ✅ All integration tests green
- ✅ CI green (multi-instance simulation)
- ✅ Documentation updated:
    - `docs/concurrency.md` created
    - `docs/scaling-guide.md` created
    - `README.md` updated with scaling instructions
- ✅ Tag `v0.12.0` created and pushed

---

## 📦 Configuration Changes

### New Configuration Files

**`application-docker.yml` updates:**

```yaml
spring:
  kafka:
    listener:
      concurrency: 3
    consumer:
      max-poll-records: 100
      fetch-min-size: 1024
      max-poll-interval-ms: 300000

  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 3

  lifecycle:
    timeout-per-shutdown-phase: 30s

server:
  shutdown: graceful

triagemate:
  outbox:
    publisher:
      batch-size: 50
      fixed-delay-ms: 200
      max-retry-count: 10
```

---

## 🔗 Related Documentation

- [Phase 10: Transactional Outbox](./Phase-10-Outbox-Pattern.md)
- [Phase 11: Circuit Breakers & Resilience](./Phase-11-Circuit-Breakers.md)
- [docs/concurrency.md](./docs/concurrency.md) *(to be created)*
- [docs/scaling-guide.md](./docs/scaling-guide.md) *(to be created)*

---

## ⚠️ Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| **Rebalancing storm** | Medium | Use static group membership if needed (future) |
| **Outbox lock contention** | Low | `SKIP LOCKED` prevents blocking |
| **DB connection exhaustion** | Medium | Pool size tuned to concurrency + buffer |
| **OOM under spike** | High | Memory limits + GC tuning + backpressure |
| **Partition skew** | Low | Monitor partition lag, repartition if needed |
| **Graceful shutdown timeout** | Low | 30s should be sufficient, tune if needed |

---

## 🧠 Architectural Impact

Phase 12 transitions the system from:

**"Works on my laptop"**

to:

**"Production-grade horizontal scalability"**

### What This Enables

1. **Elastic Scaling**
    - Scale triage instances up/down based on load
    - Kafka consumer group handles rebalancing
    - No manual coordination required

2. **High Availability**
    - Multiple instances provide redundancy
    - Failure of one instance doesn't stop processing
    - Automatic failover via Kafka coordinator

3. **Load Distribution**
    - Partitions distribute load across instances
    - No single point of bottleneck
    - Horizontal throughput scales linearly

4. **Operational Confidence**
    - Tested under concurrent load
    - Verified restart safety
    - Proven idempotency under race conditions

---

## 📝 Implementation Checklist

### Phase A: Design Validation
- [ ] Review Kafka consumer group mechanics
- [ ] Confirm concurrency configuration strategy
- [ ] Review DB isolation level requirements
- [ ] Document locking strategy

### Phase B: Configuration
- [ ] Add Kafka concurrency config
- [ ] Add Hikari pool sizing
- [ ] Add JVM tuning
- [ ] Add graceful shutdown config
- [ ] Add outbox batch tuning

### Phase C: Outbox Locking
- [ ] Implement `SELECT FOR UPDATE SKIP LOCKED`
- [ ] Test concurrent publisher safety
- [ ] Verify no double-publish

### Phase D: Testing - Single Instance Hardening
- [ ] Spike test (1000 events)
- [ ] Memory usage test
- [ ] Graceful shutdown test

### Phase E: Testing - Multi-Instance
- [ ] 2 instances steady state test
- [ ] Rebalance safety test (scale up/down)
- [ ] Kill instance mid-flight test
- [ ] Concurrent duplicate race test
- [ ] Full restart cycle test

### Phase F: Docker Simulation
- [ ] Add 2-replica compose config (temporary)
- [ ] Manual multi-instance verification
- [ ] Log partition assignment behavior

### Phase G: Documentation
- [ ] Create `docs/concurrency.md`
- [ ] Create `docs/scaling-guide.md`
- [ ] Update `README.md` with scaling section
- [ ] Document configuration rationale

### Phase H: Completion
- [ ] All tests green locally
- [ ] All tests green in CI
- [ ] Code review approval
- [ ] Tag `v0.12.0` created and pushed

---

## 💡 Strategic Note

Phase 12 is the **final infrastructure hardening** before advanced features:

- **Phase 13:** AI/ML Integration
- **Phase 14:** Multi-Region Deployment
- **Phase 15:** Advanced Analytics

After Phase 12, TriageMate has:
- ✅ Durable idempotency (Phase 9.2)
- ✅ Transactional outbox (Phase 10)
- ✅ Circuit breakers (Phase 11)
- ✅ Horizontal scalability (Phase 12)

**This is production-ready distributed systems architecture.**

---

**Document Version:** 1.0  
**Last Updated:** 2026-02-24  
**Status:** Design phase — ready for implementation  
**Next Action:** Configure Kafka concurrency settings, then implement outbox locking