package com.triagemate.triage.control.routing;

import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionOutcome;
import com.triagemate.triage.control.decision.DecisionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import static net.logstash.logback.argument.StructuredArguments.kv;

public class DefaultDecisionRouter implements DecisionRouter {

    private static final Logger log = LoggerFactory.getLogger(DefaultDecisionRouter.class);
    private final DecisionOutcomePublisher decisionOutcomePublisher;

    public DefaultDecisionRouter(DecisionOutcomePublisher decisionOutcomePublisher) {
        this.decisionOutcomePublisher = decisionOutcomePublisher;
    }

    @Override
    public void route(DecisionResult result, DecisionContext<?> context) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(context, "context must not be null");

        DecisionLogEntry logEntry = DecisionLogEntry.from(
                context.eventId(), context.eventType(), context.eventVersion(),
                result.outcome(), result.reason(), result.attributes()
        );

        log.info("Decision routed", kv("decisionLog", logEntry));

        decisionOutcomePublisher.publish(result, context);

        if (result.outcome() == DecisionOutcome.RETRY) {
            throw new RetryableDecisionException(
                    "Retry requested for eventId=" + context.eventId()
                            + ", eventType=" + context.eventType()
            );
        }
    }
}
