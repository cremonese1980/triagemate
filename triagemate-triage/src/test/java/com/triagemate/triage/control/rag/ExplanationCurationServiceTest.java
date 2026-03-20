package com.triagemate.triage.control.rag;

import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionOutcome;
import com.triagemate.triage.control.decision.DecisionResult;
import com.triagemate.triage.control.decision.ReasonCode;
import com.triagemate.triage.control.policy.PolicyFamilyProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ExplanationCurationServiceTest {

    private InMemoryExplanationRepository repository;
    private ExplanationCurationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryExplanationRepository();
        PolicyFamilyProvider familyProvider = () -> "basic-triage";
        service = new ExplanationCurationService(repository, familyProvider, 0.5);
    }

    @Test
    void curatesAcceptDecision() {
        DecisionResult result = acceptResult("RULE_ERROR_KEYWORDS", "Error keywords detected in message");
        DecisionContext<?> context = testContext();

        service.curateFromDecision(result, context);

        assertEquals(1, repository.saved.size());
        DecisionExplanation explanation = repository.saved.getFirst();
        assertEquals("ACCEPT", explanation.outcome());
        assertEquals("RULE_ERROR_KEYWORDS", explanation.classification());
        assertEquals("Error keywords detected in message", explanation.decisionReason());
    }

    @Test
    void curatesRejectDecision() {
        DecisionResult result = rejectResult("RULE_URGENT_EMAIL", "Urgent email escalation required");
        DecisionContext<?> context = testContext();

        service.curateFromDecision(result, context);

        assertEquals(1, repository.saved.size());
        assertEquals("REJECT", repository.saved.getFirst().outcome());
    }

    @Test
    void skipsRetryOutcome() {
        DecisionResult result = DecisionResult.of(
                DecisionOutcome.RETRY, "retry-reason", Map.of("decisionId", "test-id"),
                ReasonCode.RETRYABLE_CONDITION, "Retryable condition detected", "1.0.0"
        );
        service.curateFromDecision(result, testContext());
        assertTrue(repository.saved.isEmpty());
    }

    @Test
    void skipsDeferOutcome() {
        DecisionResult result = DecisionResult.of(
                DecisionOutcome.DEFER, "defer-reason", Map.of("decisionId", "test-id"),
                null, "Deferred for manual review", "1.0.0"
        );
        service.curateFromDecision(result, testContext());
        assertTrue(repository.saved.isEmpty());
    }

    @Test
    void skipsNullReason() {
        DecisionResult result = DecisionResult.of(
                DecisionOutcome.ACCEPT, null, Map.of("decisionId", "test-id"),
                ReasonCode.ACCEPTED_BY_DEFAULT, null, "1.0.0"
        );
        service.curateFromDecision(result, testContext());
        assertTrue(repository.saved.isEmpty());
    }

    @Test
    void skipsEmptyReason() {
        DecisionResult result = DecisionResult.of(
                DecisionOutcome.ACCEPT, "", Map.of("decisionId", "test-id"),
                ReasonCode.ACCEPTED_BY_DEFAULT, "   ", "1.0.0"
        );
        service.curateFromDecision(result, testContext());
        assertTrue(repository.saved.isEmpty());
    }

    @Test
    void skipsGenericReason() {
        DecisionResult result = DecisionResult.of(
                DecisionOutcome.ACCEPT, "unknown", Map.of("decisionId", "test-id"),
                ReasonCode.ACCEPTED_BY_DEFAULT, "unknown", "1.0.0"
        );
        service.curateFromDecision(result, testContext());
        assertTrue(repository.saved.isEmpty());
    }

    @Test
    void skipsDuplicateContentHash() {
        DecisionResult result = acceptResult("RULE_ERROR_KEYWORDS", "Error keywords detected");
        DecisionContext<?> context = testContext();

        service.curateFromDecision(result, context);
        assertEquals(1, repository.saved.size());

        service.curateFromDecision(result, context);
        assertEquals(1, repository.saved.size());
    }

    @Test
    void computesContentHashConsistently() {
        String hash1 = service.computeContentHash("RULE_A", "reason text");
        String hash2 = service.computeContentHash("RULE_A", "reason text");
        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length());
    }

    @Test
    void computesDifferentHashForDifferentInput() {
        String hash1 = service.computeContentHash("RULE_A", "reason text");
        String hash2 = service.computeContentHash("RULE_B", "reason text");
        assertNotEquals(hash1, hash2);
    }

    @Test
    void buildsContextSummaryTruncated() {
        String longText = "x".repeat(600);
        DecisionContext<?> context = DecisionContext.of(
                "evt-1", "InputReceived", 1, Instant.now(),
                Map.of("requestId", "req-1"), longText
        );

        String summary = service.buildContextSummary(context);
        assertNotNull(summary);
        assertTrue(summary.length() <= 500);
    }

    @Test
    void resolvesClassificationFromReasonCode() {
        DecisionResult result = acceptResult("RULE_ERROR_KEYWORDS", "Some reason");
        String classification = service.resolveClassification(result);
        assertEquals("POLICY_REJECTED", classification);
    }

    @Test
    void resolvesClassificationFromAiSuggestion() {
        DecisionResult result = DecisionResult.of(
                DecisionOutcome.ACCEPT, "original", Map.of(
                        "decisionId", "test-id",
                        "aiOverrideApplied", true,
                        "aiSuggestedClassification", "DEVICE_ERROR"
                ),
                ReasonCode.ACCEPTED_BY_DEFAULT, "AI override: device error", "1.0.0"
        );
        String classification = service.resolveClassification(result);
        assertEquals("DEVICE_ERROR", classification);
    }

    @Test
    void assignsDefaultQualityScore() {
        service.curateFromDecision(
                acceptResult("RULE_A", "Substantive reason here"),
                testContext()
        );
        assertEquals(0.5, repository.saved.getFirst().qualityScore());
    }

    @Test
    void assignsSystemCuratedBy() {
        service.curateFromDecision(
                acceptResult("RULE_A", "Substantive reason here"),
                testContext()
        );
        assertEquals("system", repository.saved.getFirst().curatedBy());
    }

    @Test
    void setsCorrectPolicyFamilyAndVersion() {
        service.curateFromDecision(
                acceptResult("RULE_A", "Substantive reason here"),
                testContext()
        );
        DecisionExplanation explanation = repository.saved.getFirst();
        assertEquals("basic-triage", explanation.policyFamily());
        assertEquals("1.0.0", explanation.policyVersion());
    }

    // --- helpers ---

    private DecisionResult acceptResult(String reasonCode, String humanReadableReason) {
        return DecisionResult.of(
                DecisionOutcome.ACCEPT, reasonCode, Map.of("decisionId", "test-id"),
                ReasonCode.POLICY_REJECTED, humanReadableReason, "1.0.0"
        );
    }

    private DecisionResult rejectResult(String reasonCode, String humanReadableReason) {
        return DecisionResult.of(
                DecisionOutcome.REJECT, reasonCode, Map.of("decisionId", "test-id"),
                ReasonCode.POLICY_REJECTED, humanReadableReason, "1.0.0"
        );
    }

    private DecisionContext<?> testContext() {
        return DecisionContext.of(
                "evt-1", "InputReceived", 1, Instant.now(),
                Map.of("requestId", "req-1"), "test payload"
        );
    }

    static class InMemoryExplanationRepository implements DecisionExplanationRepository {
        final List<DecisionExplanation> saved = new ArrayList<>();
        private final Set<String> contentHashes = new HashSet<>();
        private long nextId = 1;

        @Override
        public long save(DecisionExplanation explanation) {
            long id = nextId++;
            saved.add(new DecisionExplanation(
                    id, explanation.decisionId(), explanation.policyVersion(),
                    explanation.policyFamily(), explanation.classification(),
                    explanation.outcome(), explanation.decisionReason(),
                    explanation.decisionContextSummary(), explanation.contentHash(),
                    explanation.qualityScore(), explanation.curatedBy(),
                    explanation.createdAt(), explanation.archivedAt()
            ));
            contentHashes.add(explanation.contentHash());
            return id;
        }

        @Override
        public boolean existsByContentHash(String contentHash) {
            return contentHashes.contains(contentHash);
        }

        @Override
        public Optional<DecisionExplanation> findById(long id) {
            return saved.stream().filter(e -> e.id() == id).findFirst();
        }

        @Override
        public List<DecisionExplanation> findByClassification(String classification) {
            return saved.stream().filter(e -> classification.equals(e.classification())).toList();
        }
    }
}
