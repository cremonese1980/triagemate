package com.triagemate.triage.control.decision;

import com.triagemate.triage.control.policy.AcceptAllPolicy;
import com.triagemate.triage.control.policy.PolicyResult;
import com.triagemate.triage.control.policy.PolicyVersionProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DefaultDecisionServiceTest {

    private final CostGuard defaultCostGuard = new AlwaysAllowCostGuard();
    private final PolicyVersionProvider versionProvider = () -> "1.0.0";

    @Test
    void decideReturnsDefaultAccept() {
        DefaultDecisionService service = new DefaultDecisionService(
                List.of(new AcceptAllPolicy()), defaultCostGuard, versionProvider);
        DecisionContext<String> context = DecisionContext.of(
                "event-1",
                "triagemate.ingest.input-received",
                1,
                Instant.EPOCH,
                Map.of("requestId", "request-1", "correlationId", "corr-1"),
                "payload"
        );

        DecisionResult result = service.decide(context);

        assertEquals(DecisionOutcome.ACCEPT, result.outcome());
        assertEquals("deterministic-default-accept", result.reason());
        assertEquals("rules-v1", result.attributes().get("strategy"));
        assertNotNull(result.attributes().get("decisionId"), "decisionId must be generated");
        assertEquals(ReasonCode.ACCEPTED_BY_DEFAULT, result.reasonCode());
        assertEquals("All policies passed; accepted by default", result.humanReadableReason());
        assertEquals("1.0.0", result.policyVersion());
    }

    @Test
    void decisionOutcomeIncludesExpectedValues() {
        assertEquals(DecisionOutcome.ACCEPT, DecisionOutcome.valueOf("ACCEPT"));
        assertEquals(DecisionOutcome.REJECT, DecisionOutcome.valueOf("REJECT"));
        assertEquals(DecisionOutcome.RETRY, DecisionOutcome.valueOf("RETRY"));
        assertEquals(DecisionOutcome.DEFER, DecisionOutcome.valueOf("DEFER"));
    }

    @Test
    void decisionResultKeepsAttributes() {
        Map<String, Object> attributes = Map.of("source", "unit-test");
        DecisionResult result = new DecisionResult(DecisionOutcome.REJECT, "rejected", attributes, null, null, null);

        assertEquals(DecisionOutcome.REJECT, result.outcome());
        assertEquals("rejected", result.reason());
        assertEquals(attributes, result.attributes());
    }

    @Test
    void rejectWhenPolicyDenies() {
        DefaultDecisionService service = new DefaultDecisionService(
                List.of(ctx -> PolicyResult.deny("blocked-by-test", Map.of("rule", "test"))),
                defaultCostGuard, versionProvider
        );
        DecisionContext<String> context = DecisionContext.of(
                "event-2", "test.event", 1, Instant.EPOCH, Map.of(), "payload"
        );

        DecisionResult result = service.decide(context);

        assertEquals(DecisionOutcome.REJECT, result.outcome());
        assertEquals("blocked-by-test", result.reason());
        assertEquals("policy-rejection", result.attributes().get("strategy"));
        assertNotNull(result.attributes().get("decisionId"), "decisionId must be generated on reject");
        assertEquals(ReasonCode.POLICY_REJECTED, result.reasonCode());
        assertEquals("1.0.0", result.policyVersion());
    }

    @Test
    void rejectWhenCostGuardDenies() {
        CostGuard denyingGuard = ctx -> CostDecision.deny(99.99, "over-budget");
        DefaultDecisionService service = new DefaultDecisionService(
                List.of(new AcceptAllPolicy()), denyingGuard, versionProvider
        );
        DecisionContext<String> context = DecisionContext.of(
                "event-3", "test.event", 1, Instant.EPOCH, Map.of(), "payload"
        );

        DecisionResult result = service.decide(context);

        assertEquals(DecisionOutcome.REJECT, result.outcome());
        assertEquals("over-budget", result.reason());
        assertEquals("cost-limit-exceeded", result.attributes().get("strategy"));
        assertNotNull(result.attributes().get("decisionId"), "decisionId must be generated on cost reject");
        assertEquals(ReasonCode.COST_LIMIT_EXCEEDED, result.reasonCode());
        assertEquals("1.0.0", result.policyVersion());
    }

    @Test
    void policyVersionReflectsProvider() {
        PolicyVersionProvider v2Provider = () -> "2.0.0";
        DefaultDecisionService service = new DefaultDecisionService(
                List.of(new AcceptAllPolicy()), defaultCostGuard, v2Provider
        );
        DecisionContext<String> context = DecisionContext.of(
                "event-4", "test.event", 1, Instant.EPOCH, Map.of(), "payload"
        );

        DecisionResult result = service.decide(context);

        assertEquals("2.0.0", result.policyVersion());
    }
}
