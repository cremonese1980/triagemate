package com.triagemate.triage.routing;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.triagemate.triage.decision.DecisionContext;
import com.triagemate.triage.decision.DecisionOutcome;
import com.triagemate.triage.decision.DecisionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultDecisionRouterTest {

    private final DefaultDecisionRouter router = new DefaultDecisionRouter();
    private final DecisionContext<String> context = new DecisionContext<>(
            "event-1",
            "triagemate.ingest.input-received",
            1,
            Instant.EPOCH,
            Map.of("correlationId", "corr-1", "requestId", "req-1"),
            "payload"
    );

    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(DefaultDecisionRouter.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
    }

    @Test
    void routeAcceptLogsAtInfo() {
        router.route(DecisionResult.of(DecisionOutcome.ACCEPT, "accepted", Map.of()), context);

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        assertEquals(Level.INFO, event.getLevel());
        assertEquals(
                "ROUTED: ACCEPT — eventId=event-1, type=triagemate.ingest.input-received, reason=accepted, correlationId=corr-1, requestId=req-1",
                event.getFormattedMessage()
        );
    }

    @Test
    void routeRejectLogsAtWarn() {
        router.route(DecisionResult.of(DecisionOutcome.REJECT, "rejected", Map.of()), context);

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        assertEquals(Level.WARN, event.getLevel());
        assertEquals(
                "ROUTED: REJECT — eventId=event-1, type=triagemate.ingest.input-received, reason=rejected, correlationId=corr-1, requestId=req-1",
                event.getFormattedMessage()
        );
    }

    @Test
    void routeDeferLogsAtInfo() {
        router.route(DecisionResult.of(DecisionOutcome.DEFER, "deferred", Map.of()), context);

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        assertEquals(Level.INFO, event.getLevel());
        assertEquals(
                "ROUTED: DEFER — eventId=event-1, type=triagemate.ingest.input-received, reason=deferred, correlationId=corr-1, requestId=req-1",
                event.getFormattedMessage()
        );
    }

    @Test
    void routeRetryLogsAtInfoAndThrowsRetryableException() {
        RetryableDecisionException exception = assertThrows(
                RetryableDecisionException.class,
                () -> router.route(DecisionResult.of(DecisionOutcome.RETRY, "retry-later", Map.of()), context)
        );

        assertTrue(exception.getMessage().contains("eventId=event-1"));
        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        assertEquals(Level.INFO, event.getLevel());
        assertEquals(
                "ROUTED: RETRY — eventId=event-1, type=triagemate.ingest.input-received, reason=retry-later, correlationId=corr-1, requestId=req-1",
                event.getFormattedMessage()
        );
    }
}
