package com.triagemate.triage.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagemate.triage.observability.OutboxMetrics;
import com.triagemate.triage.persistence.JdbcOutboxRepository;
import com.triagemate.triage.persistence.OutboxEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.triagemate.triage.health.KafkaHealthIndicator;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherMetricsTest {

    @Mock
    private JdbcOutboxRepository repository;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    private OutboxMetrics metrics;
    @Mock
    private KafkaHealthIndicator kafkaHealth;

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        OutboxProperties props = new OutboxProperties();
        props.setBatchSize(10);
        props.setMaxAttempts(3);
        props.setBaseBackoffMillis(1000);
        props.setMaxBackoffMillis(300000);

        publisher = new OutboxPublisher(repository, kafkaTemplate, props, new ObjectMapper(), metrics, kafkaHealth);
        publisher.start();
    }

    @Test
    void poll_recordsPublished_onSuccessfulSend() {
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), "topic", "key", "type", "{}", OutboxStatus.PENDING, Instant.now());
        when(repository.claimBatch(anyInt(), anyString())).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        publisher.poll();

        verify(repository).markPublished(event.getId());
        verify(metrics).recordPublished();
        verify(metrics, never()).recordKafkaFailure();
    }

    @Test
    void poll_recordsFailureAndRetry_onRetriableFailure() {
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), "topic", "key", "type", "{}", OutboxStatus.PENDING, Instant.now());
        when(repository.claimBatch(anyInt(), anyString())).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        publisher.poll();

        verify(metrics).recordKafkaFailure();
        verify(metrics).recordRetry();
        verify(repository).markFailed(any(), anyInt(), any(), anyString());
    }

    @Test
    void poll_recordsValidationFailure_forEmptyPayload() {
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), "topic", "key", "type", "", OutboxStatus.PENDING, Instant.now());
        when(repository.claimBatch(anyInt(), anyString())).thenReturn(List.of(event));

        publisher.poll();

        verify(metrics).recordValidationFailure();
        verify(repository).markExhausted(event.getId(), event.getPublishAttempts(), "Empty payload");
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }
}