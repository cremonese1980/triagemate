# 🏗️ TriageMate — Structured Coding Process (Phase 8+)

> This document defines the official engineering workflow for Phase 8 and beyond.
> It replaces ad-hoc chat-driven changes with a **structured, traceable, incremental process**.
>
> **Version:** 3.0
> **Scope:** Phase 8 onward — all behavioral, infrastructural, and lifecycle changes.

---

## 📐 Core Principles

| # | Principle |
|---|-----------|
| 1 | **No code change without a Change ID** |
| 2 | **No silent modification** |
| 3 | Every change is traceable to a design decision, a scoped intent, and a Definition of Done |
| 4 | **We always know in which phase of the process we are** |
| 5 | **No speculative fixes — one change, one test run, one observation** |

---

## 🔄 Process Phases (A → E)

For each subtask (8.1, 8.2, … 8.n) we move through the following stages:

```
Phase A → Phase B → Phase C → Phase D → Phase E
 Design    Change    Implement  Verify   Commit &
 Freeze    Design               & Test    Docs
```

---

### 🅐 Phase A — Iterative Design (Theory Freeze)

**Goal:** Reach architectural clarity before touching code.

**Activities:**
- Clarify responsibility boundaries
- Define layer placement (domain vs infra)
- Identify trade-offs
- Explicitly define what we are **NOT** implementing yet

**Output — Design Freeze Artefact:**

Phase A must produce a **minimum written artefact** before closing. This replaces vague verbal agreement with verifiable substance:

```
Design Freeze — 8.X
────────────────────
Problem:     [what we are solving]
Solution:    [chosen approach, 2–3 sentences]
Excluded:    [what is explicitly NOT in scope]
Open risks:  [known unknowns, if any]
```

> ✅ When this artefact is written and agreed: *"Phase A closed for 8.X"*

**⏱️ Phase A Timeout:**

Phase A must not exceed **3 working sessions**. If design is not frozen after 3 sessions, one of the following must happen:

| Action | When |
|--------|------|
| **Force freeze** | The current best option is good enough — commit to it |
| **Descope** | The subtask is too large — split it into smaller pieces |
| **Spike** | Write a throwaway prototype to resolve the open question |

> ⛔ **No code is written in Phase A.** But Phase A cannot become infinite architecture either.

---

### 🅑 Phase B — Change Design

**Goal:** Define the modification formally before implementation.

**Impact Classification:**

Before writing the full Change Design block, classify the change:

| Level | Criteria | Required Design |
|-------|----------|-----------------|
| **Minor** | Config change, local refactor, no behavioral impact | One-line intent + scope |
| **Major** | Behavioral change, new component, cross-module impact | Full Change Design block |

**Minor Change Design (abbreviated):**

```
Change ID: TM-8.3.1
Scope:     triage module | Intent: rename constant for clarity | Impact: minor
```

**Major Change Design (full):**

```
Change ID: TM-8.1.1
Scope:      triage module
Type:       behavior change
Intent:     delegate offset commit to container (AckMode.RECORD)
Side effects:
  - remove manual ack
  - listener signature change
Risk:
  - retry semantics change
Test impact:
  - none expected
Test layer:
  - integration (Kafka Testcontainers)
Verification scenarios:
  - message failing 3x must land in DLT within 30s
  - successful message must commit offset immediately
  - consumer restart must not reprocess committed offsets
```

This freezes:
- **What** is changing
- **Why** it is changing
- **What** might break
- **Which test layer** validates it
- **How** we will verify it (for major changes)

> ✅ When Phase B is complete: *"Phase B closed for TM-8.X.X"*

---

### 🅒 Phase C — Implementation

**Goal:** Apply the change with explicit traceability.

**Rules:**
- Every code change must include a **comment header**

```java
// [TM-8.1.1] Delegate offset commit to container (AckMode.RECORD)
// Phase 8 — Error classification & retry semantics
```

**Naming convention:** `TM-<phase>.<increment>`

| Example | Meaning |
|---------|---------|
| `TM-8.1.1` | Phase 8, subtask 1, change 1 |
| `TM-8.1.2` | Phase 8, subtask 1, change 2 |
| `TM-8.2.1` | Phase 8, subtask 2, change 1 |

After implementation, **explicit confirmation is required**:

```
✅ Implemented TM-8.1.1
❌ Not implemented — reason: [...]
```

> ⛔ **No change exists until explicitly confirmed.**

---

### 🅓 Phase D — Verification

**Goal:** Validate behavior against predefined criteria at the correct test layer.

**Includes:**
- Unit tests
- Integration tests
- Manual test scenario (if relevant)

**Pass/Fail criteria:**

For **major** changes, Phase D must verify the specific scenarios defined in the Change Design (Phase B). Each scenario must be explicitly marked:

```
Verification — TM-8.1.1
────────────────────────
✅ message failing 3x lands in DLT within 30s
✅ successful message commits offset immediately
❌ consumer restart reprocesses 1 committed offset — see TM-8.1.2
```

**Failure handling:**

If Phase D verification fails, the process does **not** return to Phase C. Instead, **Phase B is reopened** — because the failure often indicates a design gap, not just an implementation bug. If after analysis the design is confirmed sound, Phase B can be closed immediately with a note and Phase C reopened.

For complex or non-deterministic failures, activate the **Failure Recovery Protocol (FRP)** — see dedicated section below.

**Stability Exit Condition:**

A change is considered stable when:
- All existing tests pass
- At least one test validates the new behavior
- No unexpected Kafka bootstrap attempts occur in test profile
- Dev and test profiles behave differently by design, not by accident

> ✅ When all scenarios pass: *"Phase D closed for TM-8.X.X"*

---

### 🅔 Phase E — Commit & Documentation

**Commit format:**

```
feat(triage): TM-8.1.1 delegate offset commit to container

- Remove manual Acknowledgment
- Introduce AckMode.RECORD
- No DLT changes
```

**Bidirectional traceability:**

The phase documentation must link Change ID ↔ Commit SHA, so navigation works in both directions:

| Change ID | Commit SHA | Summary | Status |
|-----------|------------|---------|--------|
| TM-8.1.1  | `abc1234`  | Delegate offset commit to container | ✅ stabilized |
| TM-8.1.2  | `def5678`  | Fix consumer restart offset handling | ✅ stabilized |

**Then update documentation:**

```
docs/coding-process/phase-8/8.1.md
```

---

## 🔁 Failure Recovery Protocol (FRP)

**Objective:** Prevent cognitive loss and chaotic debugging when unexpected failures appear after a change.

**Trigger Conditions — activate FRP when:**
- Tests that previously passed now fail unexpectedly
- Logs show non-deterministic or confusing behavior
- Emotional escalation or frustration is detected
- Multiple hypotheses are being generated without controlled validation

---

### FRP-A — Immediate Freeze

1. **Stop modifying code immediately.**
2. Do not add new hypotheses.
3. Do not refactor.
4. Do not delete code.
5. Capture:

```
FRP Freeze Snapshot
───────────────────
Last green commit:  [hash]
Current branch:     [name]
Change IDs applied: [TM-8.X.X, TM-8.X.X]
Symptom:            [one-line description of failure]
```

---

### FRP-B — Delta Isolation

1. Identify the last working state.
2. List all Change IDs applied since then.
3. Compare diff — only modified files, ignore formatting noise.
4. Classify each change:

| Category | Examples |
|----------|----------|
| Config | `application.yml`, profiles |
| Bean lifecycle | `@Conditional`, `@Profile`, `@Bean` |
| Profiles | `application-dev.yml`, `application-test.yml` |
| Kafka infra | Topics, `AdminClient`, bootstrap |
| Test infra | Testcontainers, `@DynamicPropertySource` |
| Serialization | JSON, Avro, headers |
| Listener / Ack / Error | `@KafkaListener`, `AckMode`, `ErrorHandler` |

---

### FRP-C — Controlled Rollback

Rollback incrementally **by Change ID**, one at a time.

For each rollback:
1. Re-run full test suite.
2. Observe: Does failure disappear? Does behavior change?
3. Record result in phase document.

> ⛔ **Never rollback multiple changes at once.**

---

### FRP-D — Root Cause Confirmation

Once the offending change is identified:
1. Reapply the change.
2. Fix properly.
3. Add missing test.
4. Update documentation in `phase-x.md`.
5. Mark Change ID as **stabilized**.

---

### FRP Golden Rule

> **No speculative fixes. One change. One test run. One observation.**

---

## 🧪 Test Matrix & Verification Strategy

**Objective:** Ensure every behavior change is validated at the correct test layer.

**Test Discipline Rule — every behavior modification must answer:**
- Which layer validates it?
- Is an existing test sufficient?
- If not, which test must be added?
- Is the test deterministic?

---

### Layer 1 — Unit Tests

**Scope:** Pure logic, decision routing, exception classification, idempotency logic, mapping and serialization.

**Requirements:**
- No Spring context required
- No Kafka required
- Must be deterministic

---

### Layer 2 — Spring Slice / Context Tests

**Scope:** Bean wiring, profile activation, conditional configs, listener configuration.

**Requirements:**
- Validate active profile behavior
- Validate bean presence / absence

**Example checks:**
- `KafkaErrorHandlingConfig` excluded in test profile
- Dev topics not created in test

---

### Layer 3 — Kafka Integration Tests (Testcontainers)

**Scope:** Producer → Broker → Consumer, ack mode behavior, DLT behavior, retry logic, duplicate detection.

**Requirements:**
- Use container bootstrap server
- Override bootstrap via `@DynamicPropertySource`
- Never rely on `localhost:9092`

**Verify:**
- Message produced
- Message consumed
- Offset committed correctly
- DLT topic used only when expected

---

### Layer 4 — Application Smoke Tests

**Scope:** Full app context startup, no infinite retry loops, no unexpected `AdminClient` bootstrap attempts in test profile.

**Requirements:**
- Explicit profile declaration
- No implicit dev activation

---

### Layer 5 — Manual Verification (When Behavior Changes)

**Required when modifying:** AckMode, ErrorHandler, retry policy, topic definitions, producer idempotency, transactional behavior.

**Manual checklist:**
1. Start Kafka (`docker compose`)
2. Produce test event
3. Observe: consumer logs, retry logs, DLT logs, offset commits, duplicate skipping
4. Stop Kafka
5. Verify graceful degradation

---

## 🧭 State Marker

**Objective:** At any moment, provide an objective, zero-interpretation snapshot of where a subtask stands.

**When asked:** *"In che stato siamo nella 8.1?"* — the answer is read directly from the State Marker. No guessing, no interpretation. Only objective state.

**Structure:**

```yaml
# State Marker — 8.1
phase: 8.1
current_stage: C        # A=Design | B=Change Design | C=Implementation | D=Verification | E=Done

applied_changes:
  - TM-8.1.1
  - TM-8.1.2

stabilized_changes:
  - TM-8.1.1

pending_verification:
  - TM-8.1.2

tests_status:
  unit: passing
  integration: failing
  manual: not_run

dod_status: not_met
last_known_green_commit: abc1234
frp_active: false        # true if Failure Recovery Protocol is engaged
```

**Reading rules:**
- `current_stage` tells you **where we are**
- `stabilized_changes` tells you **what is safe**
- `pending_verification` tells you **what needs attention**
- `dod_status` tells you **if we can close**
- `frp_active` tells you **if we're in crisis mode**

**State Marker lives in:** `docs/coding-process/phase-8/8.X.md` — updated at every stage transition.

---

## 📁 File Structure for Discipline

For each Phase subtask:

```
docs/
└── coding-process/
    └── phase-8/
        ├── 8.1.md      # includes State Marker + all Change IDs + DoD
        ├── 8.2.md
        └── 8.3.md
```

Each file contains:

1. State Marker (top of file — always current)
2. Phase Objective
3. Scope
4. Story (Scrum style)
5. Definition of Done
6. All Change IDs related to that subtask (with Commit SHAs after Phase E)
7. Notes / Trade-offs / Deferred items

> **This becomes persistent engineering memory.**

---

## ✅ Definition of Done (DoD)

Each subtask must include a **DoD section** in its phase file.

**Example — DoD for 8.1:**

- [ ] Retry classification implemented
- [ ] `AckMode.RECORD` active
- [ ] No manual ack in listeners
- [ ] All unit tests passing
- [ ] All integration tests passing
- [ ] All Phase B verification scenarios passed
- [ ] No unexpected Kafka bootstrap in test profile
- [ ] Manual retry scenario validated
- [ ] Change IDs documented with Commit SHAs
- [ ] State Marker updated to `current_stage: E`

> ⛔ **A subtask is not closed without DoD satisfied.**

---

## 🏷️ Change ID Rules

| Rule | Description |
|------|-------------|
| **Prefix** | `TM` = TriageMate |
| **Mandatory** | No modification without Change ID |
| **User changes** | User-initiated changes must also declare Change ID |
| **Assistant** | Must verify Change ID usage before proceeding |

> ⛔ If a change is discussed but **not assigned a Change ID**, implementation must not begin.

---

## 📊 Tracking Process State

At any time we can ask: **"Which phase are we in for 8.1?"**

The answer comes from the **State Marker** — no interpretation, only objective state:

| Answer | Meaning |
|--------|---------|
| *"We are in Phase A"* | Design in progress |
| *"Phase A closed"* | Design frozen with written artefact |
| *"Phase B closed, ready for Phase C"* | Change Design frozen, ready to code |
| *"Phase C complete, pending verification"* | Code written, needs testing |
| *"Phase D ongoing"* | Tests running |
| *"Phase D failed → Phase B reopened"* | Verification failed, redesigning |
| *"FRP active for TM-8.1.2"* | Failure Recovery Protocol engaged |
| *"8.1 fully closed"* | Subtask complete ✅ |

> This prevents chaotic iteration.

---

## 🤖 AI Workflow Integration

This process is designed to work seamlessly with AI-assisted development:

| Feature | Benefit |
|---------|---------|
| **Phase tracking** | AI can always respond "we are in Phase B of TM-8.1.1" |
| **State Marker** | AI reads the marker and reports objective state — zero interpretation |
| **No implicit changes** | AI must not modify code without a Change ID contract |
| **Change ID as contract** | Clear handshake between human intent and AI execution |
| **FRP triggers** | AI can detect frustration or hypothesis churn and suggest activating FRP |
| **Written artefacts** | AI has persistent context across sessions |

> The Change ID is the **contract** between developer and AI assistant.

---

## ⚠️ Pragmatism Clause

This is a process for a **learning project driven by one developer**. It must serve the engineer, not the other way around.

| Symptom | Action |
|---------|--------|
| Phase A exceeds 3 sessions | Force freeze, descope, or spike |
| Phase B feels mechanical for a trivial change | Use Minor classification |
| Process feels like ritual instead of tool | Revisit — if Phase B never reveals surprises, it's too heavy |
| A subtask is clearly simple end-to-end | Minor path: abbreviated Phase B, standard C/D/E |
| FRP feels overkill for an obvious typo | Skip it — FRP is for non-deterministic or multi-change failures |

> **The litmus test:** Does each Phase B force you to discover a side effect you hadn't considered? If yes, the process is working. If no, lighten it.

---

## 🚫 Anti-Entropy Rule

**Never:**

- Silent edits
- Implicit lifecycle changes
- Profile modifications without Change ID
- Topic creation without documented intent
- Speculative multi-change debugging

> All structural changes must pass through **A → B → C → D → E**.
> All failures must pass through **FRP-A → FRP-B → FRP-C → FRP-D**.

---

## 💡 Why This Exists

Phase 8 introduced behaviors that affect **system lifecycle**:

| Concern | Impact |
|---------|--------|
| Retry semantics | Changes consumer behavior under failure |
| Offset commit changes | Affects at-least-once vs exactly-once delivery |
| KafkaAdmin side-effects | Infrastructure-level side effects |
| DLT topics | Permanent message routing changes |
| Profile-specific behavior | Environment-dependent system behavior |

> **From Phase 8 onward, informal coding is dangerous.**

---

*End of Process Definition — v3.0*

---

> 💬 **This is not overkill. This is senior-level discipline — applied with pragmatism.**
