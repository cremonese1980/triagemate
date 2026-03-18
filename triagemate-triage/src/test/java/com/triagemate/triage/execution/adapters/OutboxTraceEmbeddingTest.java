package com.triagemate.triage.execution.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionOutcome;
import com.triagemate.triage.control.decision.DecisionResult;
import com.triagemate.triage.persistence.OutboxEvent;
import com.triagemate.triage.persistence.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Verifies that trace fields from DecisionContext are embedded
 * in the outbox payload's EventEnvelope.trace when publishing a decision outcome.
 */
@ExtendWith(MockitoExtension.class)
class OutboxTraceEmbeddingTest {

    @Mock
    private OutboxEventRepository repository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Captor
    private ArgumentCaptor<OutboxEvent> outboxCaptor;

    @InjectMocks
    private OutboxDecisionOutcomePublisher publisher;

    OutboxTraceEmbeddingTest() {
        // topic and serviceName injected via @Value in production;
        // we use reflection or construct manually for unit tests
    }

    private OutboxDecisionOutcomePublisher createPublisher() {
        return new OutboxDecisionOutcomePublisher(
                repository, objectMapper,
                "triagemate.triage.decision-made.v1",
                "triagemate-triage-test"
        );
    }

    @Test
    void publish_embedsTraceFromContext_inOutboxPayload() throws Exception {
        OutboxDecisionOutcomePublisher pub = createPublisher();

        InputReceivedV1 input = new InputReceivedV1(
                "input-1", "email", "subject", "text", "from@test", 123L);

        Map<String, String> trace = Map.of(
                "requestId", "req-trace-001",
                "correlationId", "corr-trace-002"
        );

        DecisionContext<InputReceivedV1> context = DecisionContext.of(
                "evt-original", "triagemate.ingest.input-received", 1,
                Instant.EPOCH, trace, input);

        DecisionResult result = DecisionResult.of(
                DecisionOutcome.ACCEPT, "accepted", Map.of());

        pub.publish(result, context);

        verify(repository).save(outboxCaptor.capture());
        OutboxEvent saved = outboxCaptor.getValue();

        JsonNode root = objectMapper.readTree(saved.getPayload());
        JsonNode traceNode = root.path("trace");

        assertThat(traceNode.isMissingNode()).isFalse();
        assertThat(traceNode.path("requestId").asText()).isEqualTo("req-trace-001");
        assertThat(traceNode.path("correlationId").asText()).isEqualTo("corr-trace-002");
        assertThat(traceNode.path("causationId").asText()).isEqualTo("evt-original");
    }

    @Test
    void publish_handlesNullTrace_inOutboxPayload() throws Exception {
        OutboxDecisionOutcomePublisher pub = createPublisher();

        InputReceivedV1 input = new InputReceivedV1(
                "input-2", "ticket", "subj", "body", "from@test", 456L);

        DecisionContext<InputReceivedV1> context = DecisionContext.of(
                "evt-no-trace", "triagemate.ingest.input-received", 1,
                Instant.EPOCH, null, input);

        DecisionResult result = DecisionResult.of(
                DecisionOutcome.REJECT, "rejected", Map.of());

        pub.publish(result, context);

        verify(repository).save(outboxCaptor.capture());
        OutboxEvent saved = outboxCaptor.getValue();

        JsonNode root = objectMapper.readTree(saved.getPayload());
        JsonNode traceNode = root.path("trace");

        // trace should still be present with null fields, not blow up
        assertThat(root.has("eventId")).isTrue();
    }

    @Test
    void publish_setsCorrectEventTypeAndTopic() throws Exception {
        OutboxDecisionOutcomePublisher pub = createPublisher();

        InputReceivedV1 input = new InputReceivedV1(
                "input-3", "email", "subj", "text", "from@test", 789L);

        DecisionContext<InputReceivedV1> context = DecisionContext.of(
                "evt-meta", "triagemate.ingest.input-received", 1,
                Instant.EPOCH, Map.of("requestId", "r", "correlationId", "c"), input);

        String decisionId = UUID.randomUUID().toString();
        DecisionResult result = DecisionResult.of(
                DecisionOutcome.ACCEPT, "ok", Map.of("decisionId", decisionId), null, null, "1.0.0");

        pub.publish(result, context);

        verify(repository).save(outboxCaptor.capture());
        OutboxEvent saved = outboxCaptor.getValue();

        assertThat(saved.getAggregateType()).isEqualTo("triagemate.triage.decision-made.v1");
        assertThat(saved.getEventType()).isEqualTo("triagemate.triage.decision-made");
        assertThat(saved.getAggregateId()).isEqualTo(decisionId);
    }

    @Test
    void publish_embedsDecisionVersioningMetadata() throws Exception {
        OutboxDecisionOutcomePublisher pub = createPublisher();

        InputReceivedV1 input = new InputReceivedV1(
                "input-4", "email", "subj", "text", "from@test", 790L);

        DecisionContext<InputReceivedV1> context = DecisionContext.of(
                "evt-versioned", "triagemate.ingest.input-received", 1,
                Instant.EPOCH, Map.of(), input);

        String decisionId = UUID.randomUUID().toString();
        DecisionResult result = DecisionResult.of(
                DecisionOutcome.ACCEPT, "ok", Map.of("decisionId", decisionId), null, null, "2.1.0");

        pub.publish(result, context);

        verify(repository).save(outboxCaptor.capture());
        OutboxEvent saved = outboxCaptor.getValue();

        JsonNode root = objectMapper.readTree(saved.getPayload());
        JsonNode metadataNode = root.path("metadata");

        assertThat(metadataNode.path("decisionId").asText()).isEqualTo(decisionId);
        assertThat(metadataNode.path("policyVersion").asText()).isEqualTo("2.1.0");
        assertThat(metadataNode.path("sourceEventId").asText()).isEqualTo("evt-versioned");
    }
}
