package com.triagemate.triage.observability;

import com.triagemate.triage.persistence.JdbcOutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;


/**
 * Encapsulates outbox-related metrics.
 * Counter metrics are eagerly registered to ensure they appear in Prometheus
 * even before the first event is processed.
 */

@Component
public class OutboxMetrics {

    private final Counter publishedCounter;
    private final Counter retryCounter;
    private final Counter kafkaFailureCounter;
    private final Counter validationFailureCounter;

    public OutboxMetrics(MeterRegistry registry, JdbcOutboxRepository repository) {
        this.publishedCounter = Counter.builder("triagemate.outbox.published.total")
                .description("Total outbox messages successfully published to Kafka")
                .register(registry);

        this.retryCounter = Counter.builder("triagemate.outbox.retry.total")
                .description("Total retry attempts for failed outbox publishes")
                .register(registry);

        this.kafkaFailureCounter = Counter.builder("triagemate.kafka.publish.failure.total")
                .description("Total Kafka send failures during outbox publishing")
                .register(registry);

        this.validationFailureCounter = Counter.builder("triagemate.outbox.validation.failure.total")
                .description("Total payload validation failures during outbox processing")
                .register(registry);

        Gauge.builder("triagemate.outbox.pending.count", repository, JdbcOutboxRepository::countPending)
                .description("Current number of unpublished messages in the outbox")
                .register(registry);

        Gauge.builder("triagemate.outbox.oldest.message.age.seconds", repository,
                        JdbcOutboxRepository::oldestPendingAgeSeconds)
                .description("Age in seconds of the oldest pending message")
                .register(registry);
    }

    public void recordPublished() {
        publishedCounter.increment();
    }

    public void recordRetry() {
        retryCounter.increment();
    }

    public void recordKafkaFailure() {
        kafkaFailureCounter.increment();
    }

    public void recordValidationFailure() {
        validationFailureCounter.increment();
    }

}

