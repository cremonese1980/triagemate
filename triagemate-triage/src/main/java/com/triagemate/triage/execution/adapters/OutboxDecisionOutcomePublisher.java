package com.triagemate.triage.execution.adapters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.DecisionMadeV1;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionResult;
import com.triagemate.triage.control.routing.DecisionOutcomePublisher;
import com.triagemate.triage.outbox.OutboxStatus;
import com.triagemate.triage.persistence.OutboxEvent;
import com.triagemate.triage.persistence.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OutboxDecisionOutcomePublisher implements DecisionOutcomePublisher {

    private static final String DECISION_EVENT_TYPE = "triagemate.triage.decision-made";
    private static final int DECISION_EVENT_VERSION = 1;

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final String serviceName;

    public OutboxDecisionOutcomePublisher(
            OutboxEventRepository repository,
            ObjectMapper objectMapper,
            @Value("${triagemate.kafka.topics.decision-made}") String topic,
            @Value("${spring.application.name}") String serviceName
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
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
            String json = objectMapper.writeValueAsString(envelope);

            OutboxEvent event = new OutboxEvent(
                    UUID.randomUUID(),
                    topic,                 // aggregateType = topic (per ora)
                    inputId,               // aggregateId = key
                    DECISION_EVENT_TYPE,   // eventType
                    json,                  // payload
                    OutboxStatus.PENDING,             // status
                    Instant.now()          // createdAt
            );

            repository.save(event);

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize DecisionMadeV1", e);
        }
    }

    private String extractInputId(Object payload) {
        if (payload instanceof InputReceivedV1 input) {
            return input.inputId();
        }
        return "unknown-input-id";
    }

    private String outcomeToPriority(DecisionResult result) {
        return switch (result.outcome()) {
            case ACCEPT -> "P1";
            case REJECT -> "P3";
            case RETRY, DEFER -> "P2";
        };
    }
}