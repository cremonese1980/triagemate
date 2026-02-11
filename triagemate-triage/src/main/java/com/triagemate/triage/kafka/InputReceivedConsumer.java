package com.triagemate.triage.kafka;

import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.triage.decision.DecisionContext;
import com.triagemate.triage.decision.DecisionContextFactory;
import com.triagemate.triage.decision.DecisionResult;
import com.triagemate.triage.decision.DecisionService;
import com.triagemate.triage.idempotency.EventIdIdempotencyGuard;
import com.triagemate.triage.routing.DecisionRouter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Component
public class InputReceivedConsumer {

    private static final Logger log = LoggerFactory.getLogger(InputReceivedConsumer.class);
    private final DecisionContextFactory decisionContextFactory;
    private final DecisionService decisionService;
    private final DecisionRouter decisionRouter;
    private final EventIdIdempotencyGuard idempotencyGuard;
    private final MeterRegistry meterRegistry;

    public InputReceivedConsumer(
            DecisionContextFactory decisionContextFactory,
            DecisionService decisionService,
            DecisionRouter decisionRouter,
            EventIdIdempotencyGuard idempotencyGuard,
            MeterRegistry meterRegistry
    ) {
        this.decisionContextFactory = decisionContextFactory;
        this.decisionService = decisionService;
        this.decisionRouter = decisionRouter;
        this.idempotencyGuard = idempotencyGuard;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
            topics = "${triagemate.kafka.topics.input-received}",
            groupId = "${triagemate.kafka.consumer.group-id}"
    )
    public void onMessage(
            ConsumerRecord<String, EventEnvelope<?>> record,
            Acknowledgment ack
    ) {
        EventEnvelope<?> envelope = record.value();

        if (envelope == null) {
            log.warn("Received null envelope", kv("requestId", null), kv("correlationId", null), kv("eventId", null));
            ack.acknowledge();
            return;
        }

        if (idempotencyGuard.isDuplicate(envelope.eventId())) {
            log.info(
                    "Skipping duplicate event",
                    kv("requestId", traceValue(envelope, "requestId")),
                    kv("correlationId", traceValue(envelope, "correlationId")),
                    kv("eventId", envelope.eventId()),
                    kv("decisionOutcome", "DUPLICATE")
            );
            ack.acknowledge();
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

        ack.acknowledge();
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
