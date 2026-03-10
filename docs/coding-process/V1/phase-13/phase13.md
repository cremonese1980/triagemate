# 🔵 PHASE 13 — Decision Versioning & Replay Capability

## 📊 STATE

```yaml
Status:       NOT_STARTED
Phase:        13
Change ID:    TM-13
Stage:        A  # A=Design | B=Change Design | C=Implementation | D=Verification | E=Done
Owner:        Gabriele
Branch:       feat/phase-13-decision-versioning
Depends On:   v0.12.0
Last Updated: 2026-02-24
Target Tag:   v0.13.0

Goal:
  - Persist decision artifacts (not only idempotency markers)
  - Introduce policy version tracking
  - Enable deterministic decision replay
  - Support governance-grade explainability
  - Prepare for simulation & drift detection

Applied Changes:
  - none

Stabilized Changes:
  - none

Pending Verification:
  - decision persistence completeness
  - replay determinism
  - policy version tracking
  - drift detection accuracy

Tests Status:
  unit:        pending
  integration: pending
  manual:      pending

DoD Status:           not_met
Last Green Commit:    v0.12.0
FRP Active:           false
```

**Phase Lineage:**
```
v0.12.0 (Horizontal Scale) → Phase 13 (Decision Versioning) → v0.13.0
```

---

## 🅐 Design Freeze

### Problem Statement

**Attualmente:**
- Processi evento ✅
- Produci decisione ✅
- Persisti marker idempotency ✅
- Persisti outbox ✅

**Ma NON persisti:**
- ❌ Versione della policy usata
- ❌ Decision outcome completo
- ❌ Input snapshot usato per decidere
- ❌ Attributes snapshot al momento della decisione

**Quindi:**
- ❌ Non puoi replayare decisioni storiche
- ❌ Non puoi auditare storicamente le scelte
- ❌ Non puoi confrontare policy nuove vs vecchie
- ❌ Non puoi rilevare drift comportamentale
- ❌ Non hai explainability governance-grade

### Solution: Decision Artifact Persistence

**Persisting complete decision records enables:**
1. **Replay capability** — recalculate decisions with current policy
2. **Drift detection** — compare old vs new policy outcomes
3. **Audit trail** — governance-grade explainability
4. **Policy evolution** — track changes over time
5. **Simulation** — test new policies on historical data

### Explicit Scope

| In Scope ✅ | Out of Scope ❌ |
|-------------|-----------------|
| `decisions` table schema | ML model versioning |
| Decision artifact persistence | Complex drift analytics |
| Policy version tracking | Policy version migration tooling |
| Replay engine (basic) | Real-time drift monitoring |
| Drift detection (boolean flag) | Advanced simulation framework |
| Explainability hardening | User-facing replay UI |
| Deterministic replay tests | Performance optimization |

### Non-negotiable Requirements

**After Phase 13:**
- Every decision MUST be persisted with complete artifacts
- Policy version MUST be attached to every decision
- Replay MUST be deterministic (same input + same policy = same output)
- Drift detection MUST be reliable (boolean flag)

---

## 🅑 Implementation Tasks

### 13.1 — Decision Persistence Model

#### 13.1.a — Create `decisions` table

**Deliverable:** DDL for `decisions` table

```sql
CREATE TABLE decisions (
    decision_id UUID PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL,
    policy_version VARCHAR(50) NOT NULL,
    outcome VARCHAR(50) NOT NULL,
    reason_code VARCHAR(100),
    human_readable_reason TEXT,
    input_snapshot JSONB NOT NULL,
    attributes_snapshot JSONB,
    created_at TIMESTAMP NOT NULL,
    
    CONSTRAINT fk_decisions_event 
        FOREIGN KEY (event_id) 
        REFERENCES processed_events(event_id)
);

CREATE INDEX idx_decisions_event_id ON decisions(event_id);
CREATE INDEX idx_decisions_policy_version ON decisions(policy_version);
CREATE INDEX idx_decisions_created_at ON decisions(created_at);
```

**Campi minimi:**
- `decision_id` (UUID, PK) — unique identifier
- `event_id` — link to input event (foreign key)
- `policy_version` — policy version used (e.g., "1.0.0")
- `outcome` — decision result (ACCEPT, REJECT, etc.)
- `reason_code` — machine-readable reason
- `human_readable_reason` — textual explanation
- `input_snapshot` (JSON) — input event state at decision time
- `attributes_snapshot` (JSON) — computed attributes used
- `created_at` — decision timestamp

**Indici:**
- `(event_id)` — lookup decisions by event
- `(policy_version)` — analyze decisions by policy
- `(created_at)` — temporal queries

**Acceptance:**
- Persistibile via JPA
- Queryable per `eventId`
- Queryable per `policyVersion`
- Foreign key constraint enforced

---

#### 13.1.b — JPA Entity `DecisionRecord`

**Deliverable:** JPA entity with proper JSON mapping

```java
package com.triagemate.triage.decision;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "decisions")
public class DecisionRecord {

    @Id
    @Column(name = "decision_id")
    private UUID decisionId;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "policy_version", nullable = false, length = 50)
    private String policyVersion;

    @Column(name = "outcome", nullable = false, length = 50)
    private String outcome;

    @Column(name = "reason_code", length = 100)
    private String reasonCode;

    @Column(name = "human_readable_reason", columnDefinition = "TEXT")
    private String humanReadableReason;

    @Column(name = "input_snapshot", nullable = false, columnDefinition = "JSONB")
    private String inputSnapshot;  // JSON string

    @Column(name = "attributes_snapshot", columnDefinition = "JSONB")
    private String attributesSnapshot;  // JSON string

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Constructor, getters, setters
    // No business logic inside entity
}
```

**Acceptance:**
- Mapping coerente con snapshot JSON
- No lazy loading tricks
- No business logic inside entity
- Clean separation of concerns

---

#### 13.1.c — Repository

**Deliverable:** Spring Data repository with query methods

```java
package com.triagemate.triage.decision;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DecisionRecordRepository 
        extends JpaRepository<DecisionRecord, UUID> {

    /**
     * Find decision by original event ID.
     */
    Optional<DecisionRecord> findByEventId(String eventId);

    /**
     * Find all decisions made with a specific policy version.
     */
    List<DecisionRecord> findByPolicyVersion(String policyVersion);

    /**
     * Paginated query for replay scenarios.
     */
    Page<DecisionRecord> findAll(Pageable pageable);
}
```

**Acceptance:**
- `findByEventId` for lookup
- `findByPolicyVersion` for policy analysis
- Paginazione per replay batch processing

---

### 13.2 — Policy Versioning

#### 13.2.a — Introduce PolicyVersionProvider

**Deliverable:** Componente semplice per version management

```java
package com.triagemate.triage.policy;

/**
 * Provides the current policy version.
 * 
 * Initial implementation uses a constant.
 * Future: read from config, git tag, or external service.
 */
public interface PolicyVersionProvider {
    String currentVersion();
}

@Component
class ConstantPolicyVersionProvider implements PolicyVersionProvider {
    
    @Value("${triagemate.policy.version:1.0.0}")
    private String version;
    
    @Override
    public String currentVersion() {
        return version;
    }
}
```

**Configuration:**

```yaml
# application.yml
triagemate:
  policy:
    version: "1.0.0"
```

**Acceptance:**
- Version injected into decision flow
- Configurable via properties
- Default value provided

---

#### 13.2.b — Attach policyVersion to DecisionResult

**Deliverable:** Enrich decision context without polluting domain

**Before:**

```java
public class DecisionResult {
    private String decisionId;
    private String eventId;
    private String outcome;
    private String reasonCode;
}
```

**After:**

```java
public class DecisionResult {
    private String decisionId;
    private String eventId;
    private String outcome;
    private String reasonCode;
    private String policyVersion;  // NEW
    private Map<String, Object> attributes;  // NEW - snapshot
}
```

**Integration:**

```java
@Service
public class DecisionService {
    
    private final PolicyVersionProvider versionProvider;
    
    public DecisionResult makeDecision(InputEvent event) {
        // ... decision logic ...
        
        result.setPolicyVersion(versionProvider.currentVersion());
        result.setAttributes(computedAttributes);
        
        return result;
    }
}
```

**Acceptance:**
- Ogni decision persistita con version
- Non sporca core decision logic
- Attributes snapshot catturato

---

### 13.3 — Replay Engine

#### 13.3.a — Replay Service

**Deliverable:** Service to recalculate decisions

```java
package com.triagemate.triage.replay;

import com.triagemate.triage.decision.DecisionRecord;
import com.triagemate.triage.decision.DecisionRecordRepository;
import com.triagemate.triage.decision.DecisionService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ReplayService {

    private final DecisionRecordRepository repository;
    private final DecisionService decisionService;
    private final ObjectMapper objectMapper;

    public ReplayResult replayDecision(UUID decisionId) {
        // 1. Fetch original decision
        DecisionRecord original = repository.findById(decisionId)
            .orElseThrow(() -> new DecisionNotFoundException(decisionId));
        
        // 2. Deserialize input snapshot
        InputEvent reconstructedInput = objectMapper.readValue(
            original.getInputSnapshot(),
            InputEvent.class
        );
        
        // 3. Recalculate decision with CURRENT policy
        DecisionResult newDecision = decisionService.makeDecision(
            reconstructedInput
        );
        
        // 4. Compare outcomes
        return ReplayResult.builder()
            .originalDecisionId(decisionId)
            .originalOutcome(original.getOutcome())
            .originalPolicyVersion(original.getPolicyVersion())
            .newOutcome(newDecision.getOutcome())
            .newPolicyVersion(newDecision.getPolicyVersion())
            .driftDetected(!original.getOutcome().equals(newDecision.getOutcome()))
            .build();
    }
}
```

**Input:**
- `decision_id` OR `event_id`

**Output:**
- `ReplayResult` with comparison

**Acceptance:**
- Non pubblica su Kafka (read-only operation)
- Non modifica stato (no DB writes except audit log)
- Solo calcolo comparativo

---

#### 13.3.b — Replay Comparison Model

**Deliverable:** Structured comparison result

```java
package com.triagemate.triage.replay;

public class ReplayResult {
    private UUID originalDecisionId;
    private String originalOutcome;
    private String originalPolicyVersion;
    private Map<String, Object> originalAttributes;
    
    private String newOutcome;
    private String newPolicyVersion;
    private Map<String, Object> newAttributes;
    
    private boolean driftDetected;
    private List<String> attributeDifferences;
    
    // Builder, getters
}
```

**Struttura:**
- Original outcome
- New outcome
- Diff attributes
- Drift flag (boolean)

**Acceptance:**
- Drift detection deterministica
- Clear comparison semantics
- Serializable to JSON for API responses

---

#### 13.3.c — Replay CLI endpoint (dev only)

**Deliverable:** Internal endpoint for testing

```java
package com.triagemate.triage.replay;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/replay")
@Profile("dev")  // Only in dev profile
public class ReplayController {

    private final ReplayService replayService;

    @PostMapping("/{decisionId}")
    public ReplayResult replayDecision(@PathVariable UUID decisionId) {
        return replayService.replayDecision(decisionId);
    }
    
    @PostMapping("/batch")
    public BatchReplayResult replayBatch(@RequestBody BatchReplayRequest request) {
        // Replay multiple decisions
        return replayService.replayBatch(
            request.getPolicyVersion(),
            request.getLimit()
        );
    }
}
```

**Endpoint:**
```
POST /internal/replay/{decisionId}
```

**Security:**
- Solo profilo dev
- Non esposto in docker/prod-like
- No authentication required (internal only)

**Acceptance:**
- Callable from CLI/Postman
- Returns JSON comparison
- Logs replay activity

---

### 13.4 — Explainability Hardening

#### 13.4.a — Persist human-readable reason

**Deliverable:** Non solo `reasonCode`, ma testo esplicativo

```java
public class DecisionResult {
    private String reasonCode;           // Machine-readable: "POLICY_ACCEPT_001"
    private String humanReadableReason;  // Human-readable: "Event accepted: cost within threshold"
}
```

**Example:**

```java
DecisionResult result = DecisionResult.builder()
    .outcome("ACCEPT")
    .reasonCode("POLICY_ACCEPT_001")
    .humanReadableReason(
        "Event accepted: cost $45.00 is within allowed threshold of $100.00"
    )
    .build();
```

**Acceptance:**
- Both `reasonCode` and `humanReadableReason` persisted
- Human-readable text is clear and actionable
- No sensitive data leaked in reason text

---

#### 13.4.b — Deterministic attributes snapshot

**Deliverable:** Garantire JSON serializzato ordinato

```java
@Configuration
public class ObjectMapperConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Ensure deterministic JSON serialization
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        
        // ISO-8601 dates
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        return mapper;
    }
}
```

**Garantire:**
- JSON serializzato ordinato (keys sorted alphabetically)
- Niente campi non deterministici (timestamps generati al volo)
- Consistent formatting

**Acceptance:**
- Same input → same JSON snapshot
- Replay produces identical snapshots
- No flakiness in snapshot comparison

---

#### 13.4.c — Audit completeness test

**Deliverable:** Test che verifica completezza artifacts

```java
@Test
void decisionPersisted_hasCompleteArtifacts() {
    // Given: Valid input event
    InputEvent event = createValidEvent("evt-audit-001");
    
    // When: Event is processed
    kafkaTemplate.send("triagemate.ingest.input-received.v1", event);
    
    // Then: Decision record exists
    await().atMost(5.seconds).untilAsserted(() -> {
        DecisionRecord decision = decisionRepository.findByEventId("evt-audit-001")
            .orElseThrow();
        
        // Verify all artifacts persisted
        assertThat(decision.getDecisionId()).isNotNull();
        assertThat(decision.getEventId()).isEqualTo("evt-audit-001");
        assertThat(decision.getPolicyVersion()).isEqualTo("1.0.0");
        assertThat(decision.getOutcome()).isNotEmpty();
        assertThat(decision.getReasonCode()).isNotEmpty();
        assertThat(decision.getHumanReadableReason()).isNotEmpty();
        assertThat(decision.getInputSnapshot()).isNotEmpty();
        assertThat(decision.getAttributesSnapshot()).isNotEmpty();
        assertThat(decision.getCreatedAt()).isNotNull();
    });
}
```

**Test:**
- Decision persistita
- Replay produce stesso risultato con stessa policy version

```java
@Test
void replay_withSamePolicyVersion_producesSameOutcome() {
    // Given: Decision made with policy 1.0.0
    InputEvent event = createValidEvent("evt-replay-001");
    kafkaTemplate.send("triagemate.ingest.input-received.v1", event);
    
    DecisionRecord original = await().until(
        () -> decisionRepository.findByEventId("evt-replay-001"),
        Optional::isPresent
    ).get();
    
    // When: Replay with same policy version (no policy change)
    ReplayResult result = replayService.replayDecision(original.getDecisionId());
    
    // Then: Same outcome (no drift)
    assertThat(result.isDriftDetected()).isFalse();
    assertThat(result.getNewOutcome()).isEqualTo(original.getOutcome());
}
```

**Acceptance:**
- All artifacts present
- Replay determinism verified
- No missing fields

---

### 13.5 — Verification

#### 13.5.a — Integration test: decision persistence

**Deliverable:** Test end-to-end flow

```java
@SpringBootTest
@Testcontainers
class DecisionPersistenceTest {

    @Test
    void eventProcessed_decisionRowPersisted_withPolicyVersion() {
        // Given: Valid input event
        InputEvent event = createValidEvent("evt-persist-001");
        
        // When: Event is consumed
        kafkaTemplate.send("triagemate.ingest.input-received.v1", event);
        
        // Then: Decision row presente
        await().atMost(5.seconds).untilAsserted(() -> {
            Optional<DecisionRecord> decision = 
                decisionRepository.findByEventId("evt-persist-001");
            
            assertThat(decision).isPresent();
            assertThat(decision.get().getPolicyVersion()).isEqualTo("1.0.0");
            assertThat(decision.get().getOutcome()).isNotEmpty();
        });
    }
}
```

**Acceptance:**
- Evento processato
- Decision row presente
- `policyVersion` valorizzata

---

#### 13.5.b — Drift simulation test

**Deliverable:** Test policy version change detection

```java
@Test
void policyVersionChanged_replayDetectsDrift() {
    // Given: Decision made with policy 1.0.0
    InputEvent event = createValidEvent("evt-drift-001");
    kafkaTemplate.send("triagemate.ingest.input-received.v1", event);
    
    DecisionRecord original = await().until(
        () -> decisionRepository.findByEventId("evt-drift-001"),
        Optional::isPresent
    ).get();
    
    assertThat(original.getPolicyVersion()).isEqualTo("1.0.0");
    assertThat(original.getOutcome()).isEqualTo("ACCEPT");
    
    // When: Policy version changed to 2.0.0 (more restrictive)
    updatePolicyVersion("2.0.0");
    
    // And: Replay decision
    ReplayResult result = replayService.replayDecision(original.getDecisionId());
    
    // Then: Drift detected (outcome changed)
    assertThat(result.isDriftDetected()).isTrue();
    assertThat(result.getOriginalOutcome()).isEqualTo("ACCEPT");
    assertThat(result.getNewOutcome()).isEqualTo("REJECT");
    assertThat(result.getOriginalPolicyVersion()).isEqualTo("1.0.0");
    assertThat(result.getNewPolicyVersion()).isEqualTo("2.0.0");
}
```

**Acceptance:**
- Cambiare policy version
- Replay executed
- Drift rilevato correttamente

---

#### 13.5.c — Backward compatibility

**Deliverable:** Verify Phase 9–12 invarianti

```java
@Test
void phase9Through12Invariants_stillWork() {
    // Given: Event processed (Phase 9-12 flow)
    InputEvent event = createValidEvent("evt-compat-001");
    kafkaTemplate.send("triagemate.ingest.input-received.v1", event);
    
    // Then: All previous phase contracts still hold
    await().atMost(5.seconds).untilAsserted(() -> {
        // Phase 9.2: Idempotency marker
        assertThat(processedEventsRepository.existsById("evt-compat-001"))
            .isTrue();
        
        // Phase 10: Outbox record
        List<OutboxEvent> outbox = outboxRepository.findByAggregateId(
            getDecisionId("evt-compat-001")
        );
        assertThat(outbox).hasSize(1);
        assertThat(outbox.get(0).getStatus()).isEqualTo(OutboxStatus.SENT);
        
        // Phase 13: NEW - Decision record
        Optional<DecisionRecord> decision = 
            decisionRepository.findByEventId("evt-compat-001");
        assertThat(decision).isPresent();
    });
}
```

**Acceptance:**
- Phase 9–12 invariati
- Nessun effetto su outbox
- All existing tests still green

---

## 🅒 Verification Matrix

### Integration Tests

| Test | Scenario | Expected Result |
|------|----------|-----------------|
| **13.5.a** | Event processed | Decision row persisted with policy version |
| **13.5.b** | Policy changed | Replay detects drift |
| **13.5.c** | Backward compatibility | Phase 9-12 flows unchanged |
| **13.4.c** | Audit completeness | All artifacts present |
| **13.3.a** | Replay determinism | Same policy = same outcome |

---

### Manual Verification Checklist

| Step | Command | Expected Result |
|------|---------|-----------------|
| 1 | Process event with policy v1.0.0 | Decision persisted with `policy_version = "1.0.0"` |
| 2 | Check DB: `SELECT * FROM decisions` | Row exists with complete artifacts |
| 3 | `curl -X POST localhost:8080/internal/replay/{decisionId}` | Returns comparison JSON |
| 4 | Update `triagemate.policy.version=2.0.0` | Config updated |
| 5 | Restart app | New policy version active |
| 6 | Replay old decision | Drift detected if policy logic changed |
| 7 | Process new event | Decision persisted with `policy_version = "2.0.0"` |
| 8 | Compare decisions: `SELECT outcome, policy_version FROM decisions` | Different versions visible |

---

## 🅓 Done Criteria

Phase 13 is **DONE** when:

- ✅ `decisions` table exists with complete schema
- ✅ `DecisionRecord` JPA entity created
- ✅ `DecisionRecordRepository` created and injected
- ✅ `PolicyVersionProvider` implemented and injected
- ✅ Decision artifacts persistiti (input snapshot, attributes, version)
- ✅ Policy version tracciata in every decision
- ✅ `ReplayService` implemented
- ✅ Replay deterministico funzionante (same input + same policy = same output)
- ✅ Drift detection base implementata (boolean flag)
- ✅ Explainability hardening complete (human-readable reasons)
- ✅ All integration tests green:
    - Decision persistence test ✅
    - Drift simulation test ✅
    - Backward compatibility test ✅
    - Audit completeness test ✅
- ✅ Replay CLI endpoint functional (dev profile)
- ✅ Phase 9–12 flows unchanged (backward compatible)
- ✅ CI green
- ✅ Documentation updated:
    - `docs/replay-guide.md` created
    - `docs/policy-versioning.md` created
    - `README.md` updated with replay section
- ✅ Tag `v0.13.0` created and pushed

---

## 📦 Dependencies

### Maven Dependencies (Already Present)

```xml
<!-- Spring Boot Data JPA -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Jackson (JSON) -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>

<!-- Jackson Java Time Module -->
<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-jsr310</artifactId>
</dependency>
```

**No new dependencies required.**

---

## 🔗 Related Documentation

- [Phase 10: Transactional Outbox](./Phase-10-Outbox-Pattern.md)
- [Phase 12: Horizontal Scalability](./Phase-12-Horizontal-Scale.md)
- [docs/replay-guide.md](./docs/replay-guide.md) *(to be created)*
- [docs/policy-versioning.md](./docs/policy-versioning.md) *(to be created)*

---

## ⚠️ Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| **Snapshot storage bloat** | Medium | Compress JSON, archive old decisions periodically |
| **Non-deterministic attributes** | High | Enforce deterministic serialization (sorted keys) |
| **Replay performance on large datasets** | Low | Add pagination, batch processing |
| **Policy version confusion** | Medium | Clear versioning scheme (semver), documentation |
| **Backward compatibility break** | High | Comprehensive backward compat tests |
| **Sensitive data in snapshots** | High | Sanitize PII before persistence, encrypt at rest |

---

## 🧠 Architectural Impact

Phase 13 transitions the system from:

**"Black box decision-making"**

to:

**"Governance-grade explainability & auditability"**

### What This Enables

1. **Regulatory Compliance**
    - Complete audit trail of all decisions
    - Explainability for every outcome
    - Policy version tracking for compliance reports

2. **Policy Evolution**
    - Test new policies on historical data (simulation)
    - Detect drift before production deployment
    - Compare policy performance over time

3. **Debugging & Analysis**
    - Replay specific decisions to debug issues
    - Analyze why outcomes changed
    - Root cause analysis for anomalies

4. **AI Readiness (Phase 14+)**
    - Historical dataset for ML training
    - A/B testing infrastructure ready
    - Model version tracking foundation

---

## 📝 Implementation Checklist

### Phase A: Design Validation
- [ ] Review decision persistence schema
- [ ] Confirm policy versioning approach
- [ ] Validate replay semantics
- [ ] Document explainability requirements

### Phase B: Database & Entity
- [ ] Create `decisions` table migration
- [ ] Create `DecisionRecord` JPA entity
- [ ] Create `DecisionRecordRepository`
- [ ] Add foreign key to `processed_events`

### Phase C: Policy Versioning
- [ ] Implement `PolicyVersionProvider`
- [ ] Add `policyVersion` to `DecisionResult`
- [ ] Wire version into decision flow
- [ ] Add configuration property

### Phase D: Persistence Integration
- [ ] Persist decision artifacts on every decision
- [ ] Capture input snapshot (deterministic JSON)
- [ ] Capture attributes snapshot
- [ ] Add human-readable reason

### Phase E: Replay Engine
- [ ] Implement `ReplayService`
- [ ] Implement `ReplayResult` comparison model
- [ ] Add replay CLI endpoint (dev profile)
- [ ] Test deterministic replay

### Phase F: Explainability Hardening
- [ ] Ensure deterministic JSON serialization
- [ ] Add audit completeness test
- [ ] Verify no sensitive data leaks

### Phase G: Testing
- [ ] Decision persistence integration test
- [ ] Drift simulation test
- [ ] Backward compatibility test
- [ ] Audit completeness test
- [ ] All tests green locally
- [ ] All tests green in CI

### Phase H: Documentation
- [ ] Create `docs/replay-guide.md`
- [ ] Create `docs/policy-versioning.md`
- [ ] Update `README.md` with replay section
- [ ] Document API endpoints

### Phase I: Completion
- [ ] All acceptance criteria met
- [ ] Code review approval
- [ ] CI green
- [ ] Manual verification checklist complete
- [ ] Tag `v0.13.0` created and pushed

---

## 💡 Strategic Note

Phase 13 is the **governance foundation** for:

- **Phase 14:** AI/ML Integration (requires historical dataset)
- **Phase 15:** A/B Testing Framework (requires replay capability)
- **Phase 16:** Advanced Analytics (requires complete audit trail)

After Phase 13, TriageMate has:
- ✅ Durable idempotency (Phase 9.2)
- ✅ Transactional outbox (Phase 10)
- ✅ Circuit breakers (Phase 11)
- ✅ Horizontal scalability (Phase 12)
- ✅ Decision versioning & replay (Phase 13)

**This is enterprise-grade governance & auditability.**

---

**Document Version:** 1.0  
**Last Updated:** 2026-02-24  
**Status:** Design phase — ready for implementation  
**Next Action:** Create `decisions` table schema, then implement `PolicyVersionProvider`