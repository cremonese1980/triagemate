package com.triagemate.triage.kafka;

import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.DecisionMadeV1;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.triage.decision.DecisionContext;
import com.triagemate.triage.decision.DecisionResult;
import com.triagemate.triage.routing.DecisionOutcomePublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class KafkaDecisionOutcomePublisher implements DecisionOutcomePublisher {

    private static final String DECISION_EVENT_TYPE = "triagemate.triage.decision-made";
    private static final int DECISION_EVENT_VERSION = 1;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;
    private final String serviceName;

    public KafkaDecisionOutcomePublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${triagemate.kafka.topics.decision-made}") String topic,
            @Value("${spring.application.name}") String serviceName
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.serviceName = serviceName;
    }

    @Override
    public void publish(DecisionResult result, DecisionContext<?> context) {
        String decisionEventId = UUID.randomUUID().toString();
        String inputId = extractInputId(context.payload());

        var payload = new DecisionMadeV1(
                decisionEventId,
                inputId,
                outcomeToPriority(result),
                List.of(context.eventType()),
                result.reason(),
                List.of("review-decision-trace"),
                new DecisionMadeV1.Motivation("rules", List.of(result.reason()))
        );

        var envelope = new EventEnvelope<>(
                decisionEventId,
                DECISION_EVENT_TYPE,
                DECISION_EVENT_VERSION,
                Instant.now(),
                new EventEnvelope.Producer(serviceName, null),
                new EventEnvelope.Trace(
                        context.trace() == null ? null : context.trace().get("requestId"),
                        context.trace() == null ? null : context.trace().get("correlationId"),
                        context.eventId()
                ),
                payload,
                Map.of(
                        "decisionOutcome", result.outcome().name(),
                        "sourceEventId", context.eventId()
                )
        );

        try {
            kafkaTemplate.send(topic, inputId, envelope).get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish DecisionMadeV1 to topic " + topic, e);
        }
    }

    private String extractInputId(Object payload) {
        if (payload instanceof InputReceivedV1 inputReceivedV1) {
            return inputReceivedV1.inputId();
        }
        return "unknown-input-id";
    }

    private String outcomeToPriority(DecisionResult result) {
        return switch (result.outcome()) {
            case ACCEPT -> "P1";
            case REJECT -> "P3";
            case RETRY -> "P2";
            case DEFER -> "P2";
        };
    }
}
