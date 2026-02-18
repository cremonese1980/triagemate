package com.triagemate.triage.kafka;

import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionContextFactory;
import com.triagemate.triage.control.decision.DecisionResult;
import com.triagemate.triage.control.decision.DecisionService;
import com.triagemate.triage.exception.InvalidEventException;
import com.triagemate.triage.idempotency.EventIdIdempotencyGuard;
import com.triagemate.triage.control.routing.DecisionRouter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
    private final DecisionContextFactory decisionContextFactory;
    private final DecisionService decisionService;
    private final DecisionRouter decisionRouter;
    private final EventIdIdempotencyGuard idempotencyGuard;
    private final MeterRegistry meterRegistry;
    private final Validator validator;

    public InputReceivedConsumer(
            DecisionContextFactory decisionContextFactory,
            DecisionService decisionService,
            DecisionRouter decisionRouter,
            EventIdIdempotencyGuard idempotencyGuard,
            MeterRegistry meterRegistry,
            Validator validator
    ) {
        this.decisionContextFactory = decisionContextFactory;
        this.decisionService = decisionService;
        this.decisionRouter = decisionRouter;
        this.idempotencyGuard = idempotencyGuard;
        this.meterRegistry = meterRegistry;
        this.validator = validator;
    }

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

        if (idempotencyGuard.isDuplicate(envelope.eventId())) {
            log.info(
                    "Skipping duplicate event",
                    kv("requestId", traceValue(envelope, "requestId")),
                    kv("correlationId", traceValue(envelope, "correlationId")),
                    kv("eventId", envelope.eventId()),
                    kv("decisionOutcome", "DUPLICATE")
            );

            return;
        }

        DecisionContext<InputReceivedV1> context = decisionContextFactory.fromEnvelope(envelope);

        Timer.Sample sample = Timer.start(meterRegistry);
        DecisionResult result = decisionService.decide(context);
        decisionRouter.route(result, context);
        sample.stop(Timer.builder("triagemate.decision.latency")
                .tag("outcome", result.outcome().name())
                .register(meterRegistry));

        idempotencyGuard.markProcessed(envelope.eventId());

        log.info(
                "Decision completed",
                kv("requestId", traceValue(envelope, "requestId")),
                kv("correlationId", traceValue(envelope, "correlationId")),
                kv("eventId", envelope.eventId()),
                kv("decisionOutcome", result.outcome().name())
        );

    }

    private void validate(EventEnvelope<?> envelope){

        Set<ConstraintViolation<EventEnvelope<?>>> violations =
                validator.validate(envelope);

        if (!violations.isEmpty()) {
            throw new InvalidEventException("Invalid event envelope: " + violations);
        }
    }

    private String traceValue(EventEnvelope<?> envelope, String key) {
        if (envelope.trace() == null) {
            return null;
        }
        return switch (key) {
            case "requestId" -> envelope.trace().requestId();
            case "correlationId" -> envelope.trace().correlationId();
            default -> null;
        };
    }
}
