package com.triagemate.triage.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.triagemate.triage.health.KafkaHealthIndicator;
import com.triagemate.triage.observability.OutboxMetrics;
import com.triagemate.triage.persistence.JdbcOutboxRepository;
import com.triagemate.triage.persistence.OutboxEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherMdcTest {

    @Mock
    private JdbcOutboxRepository repository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private KafkaHealthIndicator kafkaHealth;

    private OutboxPublisher publisher;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        OutboxProperties props = new OutboxProperties();
        props.setBatchSize(10);
        props.setMaxAttempts(3);
        props.setBaseBackoffMillis(1000);
        props.setMaxBackoffMillis(300000);

        publisher = new OutboxPublisher(repository, kafkaTemplate, props, objectMapper,

                new OutboxMetrics(new SimpleMeterRegistry(), repository), kafkaHealth);

        publisher.start();

    }

    @AfterEach
    void tearDown() {
        publisher.stop();
        MDC.clear();
    }

    @Test
    void poll_restoresMdcFromPayload_beforePublish() {
        String payload = """
                {
                  "eventId": "evt-999",
                  "eventType": "triagemate.triage.decision-made",
                  "eventVersion": 1,
                  "occurredAt": "2025-01-01T00:00:00Z",
                  "trace": {
                    "requestId": "req-abc",
                    "correlationId": "corr-xyz"
                  },
                  "payload": {}
                }
                """;

        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(), "topic", "key", "type",
                payload, OutboxStatus.PENDING, Instant.now()
        );

        when(repository.claimBatch(any(Integer.class), anyString()))
                .thenReturn(List.of(event));

        AtomicReference<String> capturedRequestId = new AtomicReference<>();
        AtomicReference<String> capturedCorrelationId = new AtomicReference<>();
        AtomicReference<String> capturedEventId = new AtomicReference<>();

        var sendResult = new SendResult<>(
                new ProducerRecord<String, String>("topic", "value"),
                new RecordMetadata(new TopicPartition("topic", 0), 0, 0, 0, 0, 0)
        );

        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    capturedRequestId.set(MDC.get("requestId"));
                    capturedCorrelationId.set(MDC.get("correlationId"));
                    capturedEventId.set(MDC.get("eventId"));
                    return CompletableFuture.completedFuture(sendResult);
                });

        publisher.poll();

        assertThat(capturedRequestId.get()).isEqualTo("req-abc");
        assertThat(capturedCorrelationId.get()).isEqualTo("corr-xyz");
        assertThat(capturedEventId.get()).isEqualTo("evt-999");
    }

    @Test
    void poll_clearsMdc_afterEachMessage() {
        String payload = """
                {
                  "eventId": "evt-1",
                  "trace": { "requestId": "req-1", "correlationId": "corr-1" },
                  "payload": {}
                }
                """;

        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(), "topic", "key", "type",
                payload, OutboxStatus.PENDING, Instant.now()
        );

        when(repository.claimBatch(any(Integer.class), anyString()))
                .thenReturn(List.of(event));

        var sendResult = new SendResult<>(
                new ProducerRecord<String, String>("topic", "value"),
                new RecordMetadata(new TopicPartition("topic", 0), 0, 0, 0, 0, 0)
        );
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        publisher.poll();

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void poll_handlesMissingTrace_gracefully() {
        String payload = """
                {
                  "eventId": "evt-no-trace",
                  "eventType": "some-type",
                  "payload": {}
                }
                """;

        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(), "topic", "key", "type",
                payload, OutboxStatus.PENDING, Instant.now()
        );

        when(repository.claimBatch(any(Integer.class), anyString()))
                .thenReturn(List.of(event));

        AtomicReference<String> capturedEventId = new AtomicReference<>();

        var sendResult = new SendResult<>(
                new ProducerRecord<String, String>("topic", "value"),
                new RecordMetadata(new TopicPartition("topic", 0), 0, 0, 0, 0, 0)
        );

        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    capturedEventId.set(MDC.get("eventId"));
                    return CompletableFuture.completedFuture(sendResult);
                });

        publisher.poll();

        assertThat(capturedEventId.get()).isEqualTo("evt-no-trace");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void poll_handlesMalformedPayload_gracefully() {
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(), "topic", "key", "type",
                "not-json", OutboxStatus.PENDING, Instant.now()
        );

        when(repository.claimBatch(any(Integer.class), anyString()))
                .thenReturn(List.of(event));

        var sendResult = new SendResult<>(
                new ProducerRecord<String, String>("topic", "value"),
                new RecordMetadata(new TopicPartition("topic", 0), 0, 0, 0, 0, 0)
        );
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        // Should not throw — publish should proceed without MDC
        publisher.poll();

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}