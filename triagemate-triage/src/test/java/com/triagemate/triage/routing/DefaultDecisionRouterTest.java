package com.triagemate.triage.routing;

import com.triagemate.triage.decision.DecisionContext;
import com.triagemate.triage.decision.DecisionOutcome;
import com.triagemate.triage.decision.DecisionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultDecisionRouterTest {

    @Mock
    private DecisionOutcomePublisher publisher;

    private DefaultDecisionRouter router;

    @BeforeEach
    void setUp() {
        router = new DefaultDecisionRouter(publisher);
    }

    private final DecisionContext<String> context = new DecisionContext<>(
            "event-1",
            "triagemate.ingest.input-received",
            1,
            Instant.EPOCH,
            Map.of("correlationId", "corr-1", "requestId", "req-1"),
            "payload"
    );

    @Test
    void routeAcceptPublishesDecision() {
        DecisionResult result = DecisionResult.of(DecisionOutcome.ACCEPT, "accepted", Map.of());

        router.route(result, context);

        verify(publisher, times(1)).publish(result, context);
    }

    @Test
    void routeRejectPublishesDecision() {
        DecisionResult result = DecisionResult.of(DecisionOutcome.REJECT, "rejected", Map.of());

        router.route(result, context);

        verify(publisher, times(1)).publish(result, context);
    }

    @Test
    void routeDeferPublishesDecision() {
        DecisionResult result = DecisionResult.of(DecisionOutcome.DEFER, "deferred", Map.of());

        router.route(result, context);

        verify(publisher, times(1)).publish(result, context);
    }

    @Test
    void routeRetryPublishesAndThrowsRetryableException() {
        DecisionResult result = DecisionResult.of(DecisionOutcome.RETRY, "retry-later", Map.of());

        assertThrows(
                RetryableDecisionException.class,
                () -> router.route(result, context)
        );

        verify(publisher, times(1)).publish(result, context);
    }

    @Test
    void routeThrowsNullPointerExceptionForNullResult() {
        assertThrows(
                NullPointerException.class,
                () -> router.route(null, context)
        );
    }

    @Test
    void routeThrowsNullPointerExceptionForNullContext() {
        DecisionResult result = DecisionResult.of(DecisionOutcome.ACCEPT, "test", Map.of());

        assertThrows(
                NullPointerException.class,
                () -> router.route(result, null)
        );
    }
}
