package com.triagemate.triage.kafka;

import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.triage.control.execution.DecisionExecution;
import com.triagemate.triage.control.execution.InputReceivedProcessor;
import com.triagemate.triage.exception.InvalidEventException;
import com.triagemate.triage.control.routing.DecisionRouter;
import com.triagemate.triage.idempotency.EventIdIdempotencyGuard;
import com.triagemate.triage.support.TraceSupport;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolation;

import java.util.Set;

@Component
public class InputReceivedConsumer {

    private static final Logger log = LoggerFactory.getLogger(InputReceivedConsumer.class);

    private final DecisionRouter decisionRouter;
    private final InputReceivedProcessor inputReceivedProcessor;
    private final Validator validator;
    private final EventIdIdempotencyGuard idempotencyGuard;

    public InputReceivedConsumer(
            DecisionRouter decisionRouter,
            InputReceivedProcessor inputReceivedProcessor,
            Validator validator,
            EventIdIdempotencyGuard idempotencyGuard
    ) {
        this.decisionRouter = decisionRouter;
        this.inputReceivedProcessor = inputReceivedProcessor;
        this.validator = validator;
        this.idempotencyGuard = idempotencyGuard;
    }

    /**
     * Atomic outbox flow: a single DB transaction wraps:
     * 1. tryMarkProcessed() — idempotency claim (INSERT ON CONFLICT DO NOTHING)
     * 2. process() — decision logic (pure, no DB writes)
     * 3. route() → OutboxDecisionOutcomePublisher.publish() — INSERT into outbox_events
     *
     * If any step fails, the entire transaction rolls back: no orphan claims, no lost events.
     * The outbox publisher (async, outside this TX) polls and publishes to Kafka.
     */
    @Transactional
    @KafkaListener(
            topics = "${triagemate.kafka.topics.input-received}",
            groupId = "${triagemate.kafka.consumer.group-id}"
    )
    public void onMessage(ConsumerRecord<String, EventEnvelope<?>> record) {

        EventEnvelope<?> envelope = record.value();

        if (envelope == null) {
            log.warn("Received null envelope");
            return;
        }

        // Populate MDC for structured logging correlation
        MDC.put("requestId", TraceSupport.requestId(envelope));
        MDC.put("correlationId", TraceSupport.correlationId(envelope));
        MDC.put("eventId", envelope.eventId());

        try {
            validate(envelope);

            // Atomic: idempotency claim + outbox write in the same @Transactional boundary
            if (!idempotencyGuard.tryMarkProcessed(envelope.eventId())) {
                return; // duplicate, stop immediately
            }

            DecisionExecution decisionExecution = inputReceivedProcessor.process(envelope);

            decisionRouter.route(decisionExecution.result(), decisionExecution.context());

            MDC.put("decisionOutcome", decisionExecution.result().outcome().name());

            log.info("Decision completed");
        } finally {
            MDC.clear();
        }
    }

    private void validate(EventEnvelope<?> envelope){

        Set<ConstraintViolation<EventEnvelope<?>>> violations =
                validator.validate(envelope);

        if (!violations.isEmpty()) {
            throw new InvalidEventException("Invalid event envelope: " + violations);
        }
    }


}
