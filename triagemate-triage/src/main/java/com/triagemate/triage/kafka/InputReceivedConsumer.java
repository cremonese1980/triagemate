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
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolation;

import java.util.Set;

import static net.logstash.logback.argument.StructuredArguments.kv;

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
     * Claim-first idempotency: the event is claimed in the DB (INSERT ON CONFLICT DO NOTHING)
     * BEFORE any business logic or Kafka publish. If routing fails after the claim, the event
     * is permanently marked as processed and Kafka retries will short-circuit as duplicate.
     * <p>
     * Known crash-window: if the process crashes between tryMarkProcessed() and route(),
     * the decision is never emitted but the event is marked as processed.
     * This is an accepted trade-off for Phase 9.2. Phase 10 (Transactional Outbox) will
     * eliminate this window by persisting the outgoing event in the same DB transaction.
     */
    @KafkaListener(
            topics = "${triagemate.kafka.topics.input-received}",
            groupId = "${triagemate.kafka.consumer.group-id}"
    )
    public void onMessage(ConsumerRecord<String, EventEnvelope<?>> record) {

        EventEnvelope<?> envelope = record.value();

        if (envelope == null) {
            log.warn("Received null envelope", kv("requestId", null), kv("correlationId", null), kv("eventId", null));
            return;
        }

        validate(envelope);

        // Claim-first: INSERT committed before routing. See Javadoc for crash-window note.
        if (!idempotencyGuard.tryMarkProcessed(envelope.eventId())) {
            return; // duplicate, stop immediately
        }

        DecisionExecution decisionExecution = inputReceivedProcessor.process(envelope);

        decisionRouter.route(decisionExecution.result(), decisionExecution.context());


        log.info(
                "Decision completed",
                kv("requestId", TraceSupport.requestId(envelope)),
                kv("correlationId", TraceSupport.correlationId(envelope)),
                kv("eventId", envelope.eventId()),
                kv("decisionOutcome", decisionExecution.result().outcome().name())
        );

    }

    private void validate(EventEnvelope<?> envelope){

        Set<ConstraintViolation<EventEnvelope<?>>> violations =
                validator.validate(envelope);

        if (!violations.isEmpty()) {
            throw new InvalidEventException("Invalid event envelope: " + violations);
        }
    }


}
