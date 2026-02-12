package com.triagemate.triage.control.decision;

import com.triagemate.triage.control.policy.AcceptAllPolicy;
import com.triagemate.triage.control.policy.PolicyResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultDecisionServiceTest {

    @Test
    void decideReturnsDefaultAccept() {
        DefaultDecisionService service = new DefaultDecisionService(List.of(new AcceptAllPolicy()));
        DecisionContext<String> context = new DecisionContext<>(
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
        DecisionResult result = new DecisionResult(DecisionOutcome.REJECT, "rejected", attributes);

        assertEquals(DecisionOutcome.REJECT, result.outcome());
        assertEquals("rejected", result.reason());
        assertEquals(attributes, result.attributes());
    }

    @Test
    void rejectWhenPolicyDenies() {
        DefaultDecisionService service = new DefaultDecisionService(List.of(
                ctx -> PolicyResult.deny("blocked-by-test", Map.of("rule", "test"))
        ));
        DecisionContext<String> context = new DecisionContext<>(
                "event-2", "test.event", 1, Instant.EPOCH, Map.of(), "payload"
        );

        DecisionResult result = service.decide(context);

        assertEquals(DecisionOutcome.REJECT, result.outcome());
        assertEquals("blocked-by-test", result.reason());
        assertEquals("policy-rejection", result.attributes().get("strategy"));
    }
}
