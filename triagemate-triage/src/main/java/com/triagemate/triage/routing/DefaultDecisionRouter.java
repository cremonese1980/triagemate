package com.triagemate.triage.routing;

import com.triagemate.triage.decision.DecisionContext;
import com.triagemate.triage.decision.DecisionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DefaultDecisionRouter implements DecisionRouter {

    private static final Logger log = LoggerFactory.getLogger(DefaultDecisionRouter.class);

    @Override
    public void route(DecisionResult result, DecisionContext<?> context) {
        switch (result.outcome()) {
            case ACCEPT -> handleAccept(result, context);
            case REJECT -> handleReject(result, context);
            case DEFER -> handleDefer(result, context);
            case RETRY -> handleRetry(result, context);
        }
    }

    private void handleAccept(DecisionResult result, DecisionContext<?> context) {
        log.info(
                "ROUTED: ACCEPT — eventId={}, type={}, reason={}, correlationId={}, requestId={}",
                context.eventId(),
                context.eventType(),
                result.reason(),
                traceValue(context, "correlationId"),
                traceValue(context, "requestId")
        );
    }

    private void handleReject(DecisionResult result, DecisionContext<?> context) {
        log.warn(
                "ROUTED: REJECT — eventId={}, type={}, reason={}, correlationId={}, requestId={}",
                context.eventId(),
                context.eventType(),
                result.reason(),
                traceValue(context, "correlationId"),
                traceValue(context, "requestId")
        );
    }

    private void handleDefer(DecisionResult result, DecisionContext<?> context) {
        log.info(
                "ROUTED: DEFER — eventId={}, type={}, reason={}, correlationId={}, requestId={}",
                context.eventId(),
                context.eventType(),
                result.reason(),
                traceValue(context, "correlationId"),
                traceValue(context, "requestId")
        );
    }

    private void handleRetry(DecisionResult result, DecisionContext<?> context) {
        log.info(
                "ROUTED: RETRY — eventId={}, type={}, reason={}, correlationId={}, requestId={}",
                context.eventId(),
                context.eventType(),
                result.reason(),
                traceValue(context, "correlationId"),
                traceValue(context, "requestId")
        );

        throw new RetryableDecisionException(
                "Retry requested for eventId=" + context.eventId() + ", eventType=" + context.eventType()
        );
    }

    private String traceValue(DecisionContext<?> context, String key) {
        if (context.trace() == null) {
            return null;
        }
        return context.trace().get(key);
    }
}
