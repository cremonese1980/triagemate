package com.triagemate.triage.observability;

import com.triagemate.triage.persistence.JdbcOutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxMetricsTest {

    private SimpleMeterRegistry registry;
    private OutboxMetrics metrics;
    private JdbcOutboxRepository repository;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        repository = mock(JdbcOutboxRepository.class);
        when(repository.countPending()).thenReturn(0L);
        when(repository.oldestPendingAgeSeconds()).thenReturn(0.0);
        metrics = new OutboxMetrics(registry, repository);
    }

    @Test
    void counters_shouldBeRegisteredEagerly() {
        assertThat(registry.counter("triagemate.outbox.published.total").count()).isEqualTo(0.0);
        assertThat(registry.counter("triagemate.outbox.retry.total").count()).isEqualTo(0.0);
        assertThat(registry.counter("triagemate.kafka.publish.failure.total").count()).isEqualTo(0.0);
        assertThat(registry.counter("triagemate.outbox.validation.failure.total").count()).isEqualTo(0.0);
    }

    @Test
    void recorders_shouldIncrementRelatedCounters() {
        metrics.recordPublished();
        metrics.recordPublished();
        metrics.recordRetry();
        metrics.recordKafkaFailure();
        metrics.recordValidationFailure();

        assertThat(registry.counter("triagemate.outbox.published.total").count()).isEqualTo(2.0);
        assertThat(registry.counter("triagemate.outbox.retry.total").count()).isEqualTo(1.0);
        assertThat(registry.counter("triagemate.kafka.publish.failure.total").count()).isEqualTo(1.0);
        assertThat(registry.counter("triagemate.outbox.validation.failure.total").count()).isEqualTo(1.0);
    }

    @Test
    void gauges_shouldReflectRepositoryValues() {
        when(repository.countPending()).thenReturn(42L);
        when(repository.oldestPendingAgeSeconds()).thenReturn(45.7);

        assertThat(registry.get("triagemate.outbox.pending.count").gauge().value()).isEqualTo(42.0);
        assertThat(registry.get("triagemate.outbox.oldest.message.age.seconds").gauge().value()).isEqualTo(45.7);
    }
}