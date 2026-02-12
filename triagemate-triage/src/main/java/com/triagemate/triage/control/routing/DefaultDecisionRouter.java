package com.triagemate.triage.control.routing;

import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionOutcome;
import com.triagemate.triage.control.decision.DecisionResult;

import java.util.Objects;

public class DefaultDecisionRouter implements DecisionRouter {

    private final DecisionOutcomePublisher decisionOutcomePublisher;

    public DefaultDecisionRouter(DecisionOutcomePublisher decisionOutcomePublisher) {
        this.decisionOutcomePublisher = decisionOutcomePublisher;
    }

    @Override
    public void route(DecisionResult result, DecisionContext<?> context) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(context, "context must not be null");

        decisionOutcomePublisher.publish(result, context);

        if (result.outcome() == DecisionOutcome.RETRY) {
            throw new RetryableDecisionException(
                    "Retry requested for eventId=" + context.eventId()
                            + ", eventType=" + context.eventType()
            );
        }
    }
}
