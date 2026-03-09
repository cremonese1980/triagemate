package com.triagemate.triage.kafka;

import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionOutcome;
import com.triagemate.triage.control.decision.DecisionResult;
import com.triagemate.triage.control.execution.DecisionExecution;
import com.triagemate.triage.control.execution.InputReceivedProcessor;
import com.triagemate.triage.control.routing.DecisionRouter;
import com.triagemate.triage.idempotency.EventIdIdempotencyGuard;
import com.triagemate.triage.observability.DecisionMetrics;
import jakarta.validation.Validator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InputReceivedConsumerTest {

    @Mock private DecisionRouter decisionRouter;
    @Mock private InputReceivedProcessor inputReceivedProcessor;
    @Mock private Validator validator;
    @Mock private EventIdIdempotencyGuard idempotencyGuard;
    @Mock private DecisionMetrics decisionMetrics;

    private InputReceivedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new InputReceivedConsumer(
                decisionRouter, inputReceivedProcessor, validator, idempotencyGuard, decisionMetrics
        );
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void onMessage_populatesMdc_beforeBusinessLogic() {
        EventEnvelope<InputReceivedV1> envelope = createEnvelope(
                "evt-1", "req-123", "corr-456");

        ConsumerRecord<String, EventEnvelope<?>> record = new ConsumerRecord<>(
                "topic", 0, 0, "key", envelope);

        when(validator.validate(any())).thenReturn(Set.of());
        when(idempotencyGuard.tryMarkProcessed("evt-1")).thenReturn(true);

        DecisionContext<InputReceivedV1> context = DecisionContext.of(
                "evt-1", "triagemate.ingest.input-received", 1,
                Instant.EPOCH, Map.of("requestId", "req-123", "correlationId", "corr-456"),
                envelope.payload());
        DecisionResult result = DecisionResult.of(
                DecisionOutcome.ACCEPT, "ok", Map.of());
        DecisionExecution execution = new DecisionExecution(result, context);

        when(inputReceivedProcessor.process(any())).thenReturn(execution);

        AtomicReference<Map<String, String>> capturedMdc = new AtomicReference<>();

        doAnswer(inv -> {
            capturedMdc.set(MDC.getCopyOfContextMap());
            return null;
        }).when(decisionRouter).route(any(), any());

        consumer.onMessage(record);

        Map<String, String> mdc = capturedMdc.get();
        assertThat(mdc).isNotNull();
        assertThat(mdc.get("requestId")).isEqualTo("req-123");
        assertThat(mdc.get("correlationId")).isEqualTo("corr-456");
        assertThat(mdc.get("eventId")).isEqualTo("evt-1");
    }

    @Test
    void onMessage_clearsMdc_afterProcessing() {
        EventEnvelope<InputReceivedV1> envelope = createEnvelope(
                "evt-2", "req-abc", "corr-def");

        ConsumerRecord<String, EventEnvelope<?>> record = new ConsumerRecord<>(
                "topic", 0, 0, "key", envelope);

        when(validator.validate(any())).thenReturn(Set.of());
        when(idempotencyGuard.tryMarkProcessed("evt-2")).thenReturn(true);

        DecisionContext<InputReceivedV1> context = DecisionContext.of(
                "evt-2", "triagemate.ingest.input-received", 1,
                Instant.EPOCH, Map.of(), envelope.payload());
        DecisionResult result = DecisionResult.of(
                DecisionOutcome.ACCEPT, "ok", Map.of());
        when(inputReceivedProcessor.process(any()))
                .thenReturn(new DecisionExecution(result, context));

        consumer.onMessage(record);

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void onMessage_handlesNullTrace_withGeneratedFallbacks() {
        InputReceivedV1 payload = new InputReceivedV1(
                "input-1", "email", "subj", "text", "from@test", 123L);

        EventEnvelope<InputReceivedV1> envelope = new EventEnvelope<>(
                "evt-3", "triagemate.ingest.input-received", 1,
                Instant.EPOCH, null, null, payload, Map.of());

        ConsumerRecord<String, EventEnvelope<?>> record = new ConsumerRecord<>(
                "topic", 0, 0, "key", envelope);

        when(validator.validate(any())).thenReturn(Set.of());
        when(idempotencyGuard.tryMarkProcessed("evt-3")).thenReturn(true);

        DecisionContext<InputReceivedV1> context = DecisionContext.of(
                "evt-3", "triagemate.ingest.input-received", 1,
                Instant.EPOCH, Map.of(), payload);
        DecisionResult result = DecisionResult.of(
                DecisionOutcome.ACCEPT, "ok", Map.of());
        when(inputReceivedProcessor.process(any()))
                .thenReturn(new DecisionExecution(result, context));

        AtomicReference<Map<String, String>> capturedMdc = new AtomicReference<>();
        doAnswer(inv -> {
            capturedMdc.set(MDC.getCopyOfContextMap());
            return null;
        }).when(decisionRouter).route(any(), any());

        consumer.onMessage(record);

        Map<String, String> mdc = capturedMdc.get();
        assertThat(mdc).isNotNull();
        assertThat(mdc.get("requestId")).startsWith("generated-");
        assertThat(mdc.get("correlationId")).startsWith("generated-");
        assertThat(mdc.get("eventId")).isEqualTo("evt-3");
    }

    @Test
    void onMessage_clearsMdc_evenOnException() {
        EventEnvelope<InputReceivedV1> envelope = createEnvelope(
                "evt-4", "req-err", "corr-err");

        ConsumerRecord<String, EventEnvelope<?>> record = new ConsumerRecord<>(
                "topic", 0, 0, "key", envelope);

        when(validator.validate(any())).thenReturn(Set.of());
        when(idempotencyGuard.tryMarkProcessed("evt-4")).thenReturn(true);
        when(inputReceivedProcessor.process(any()))
                .thenThrow(new RuntimeException("boom"));

        try {
            consumer.onMessage(record);
        } catch (RuntimeException ignored) {
            // expected
        }

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void onMessage_skipsNullEnvelope() {
        ConsumerRecord<String, EventEnvelope<?>> record = new ConsumerRecord<>(
                "topic", 0, 0, "key", null);

        consumer.onMessage(record);

        verify(inputReceivedProcessor, never()).process(any());
    }

    @Test
    void onMessage_skipsDuplicateEvent() {
        EventEnvelope<InputReceivedV1> envelope = createEnvelope(
                "evt-dup", "req-dup", "corr-dup");

        ConsumerRecord<String, EventEnvelope<?>> record = new ConsumerRecord<>(
                "topic", 0, 0, "key", envelope);

        when(validator.validate(any())).thenReturn(Set.of());
        when(idempotencyGuard.tryMarkProcessed("evt-dup")).thenReturn(false);

        consumer.onMessage(record);

        verify(inputReceivedProcessor, never()).process(any());
    }

    private EventEnvelope<InputReceivedV1> createEnvelope(
            String eventId, String requestId, String correlationId) {

        InputReceivedV1 payload = new InputReceivedV1(
                "input-1", "email", "subj", "text", "from@test", 123L);

        return new EventEnvelope<>(
                eventId,
                "triagemate.ingest.input-received",
                1,
                Instant.EPOCH,
                new EventEnvelope.Producer("test-service", "test"),
                new EventEnvelope.Trace(requestId, correlationId, null),
                payload,
                Map.of()
        );
    }
}
