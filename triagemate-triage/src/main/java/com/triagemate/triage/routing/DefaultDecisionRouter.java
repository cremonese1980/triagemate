package com.triagemate.triage.routing;

import com.triagemate.triage.decision.DecisionContext;
import com.triagemate.triage.decision.DecisionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class DefaultDecisionRouter implements DecisionRouter {

    private static final Logger log = LoggerFactory.getLogger(DefaultDecisionRouter.class);
    private static final String ROUTE_LOG_TEMPLATE = "ROUTED: {} — eventId={}, type={}, reason={}, correlationId={}, requestId={}";

    @Override
    public void route(DecisionResult result, DecisionContext<?> context) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(context, "context must not be null");

        switch (result.outcome()) {
            case ACCEPT -> handleAccept(result, context);
            case REJECT -> handleReject(result, context);
            case DEFER -> handleDefer(result, context);
            case RETRY -> handleRetry(result, context);
        }
    }

    private void handleAccept(DecisionResult result, DecisionContext<?> context) {
        log.info(ROUTE_LOG_TEMPLATE, routingLogArgs("ACCEPT", result, context));
    }

    private void handleReject(DecisionResult result, DecisionContext<?> context) {
        log.warn(ROUTE_LOG_TEMPLATE, routingLogArgs("REJECT", result, context));
    }

    private void handleDefer(DecisionResult result, DecisionContext<?> context) {
        log.info(ROUTE_LOG_TEMPLATE, routingLogArgs("DEFER", result, context));
    }

    private void handleRetry(DecisionResult result, DecisionContext<?> context) {
        log.info(ROUTE_LOG_TEMPLATE, routingLogArgs("RETRY", result, context));

        throw new RetryableDecisionException(
                "Retry requested for eventId=" + context.eventId() + ", eventType=" + context.eventType()
        );
    }

    private Object[] routingLogArgs(String outcome, DecisionResult result, DecisionContext<?> context) {
        return new Object[]{
                outcome,
                context.eventId(),
                context.eventType(),
                result.reason(),
                traceValue(context, "correlationId"),
                traceValue(context, "requestId")
        };
    }

    private String traceValue(DecisionContext<?> context, String key) {
        if (context.trace() == null) {
            return null;
        }
        return context.trace().get(key);
    }
}
