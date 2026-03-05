package com.triagemate.triage.outbox;

import com.triagemate.triage.persistence.JdbcOutboxRepository;
import com.triagemate.triage.persistence.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final JdbcOutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;
    private final String instanceId;

    public OutboxPublisher(
            JdbcOutboxRepository repository,
            @Qualifier("outboxKafkaTemplate")
            KafkaTemplate<String, String> kafkaTemplate,
            OutboxProperties properties
    ) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.instanceId = resolveHostname() + "-" + UUID.randomUUID();
    }

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }

    @Scheduled(fixedDelayString = "${triagemate.outbox.poll-interval-ms:2000}")
    public void poll() {

        List<OutboxEvent> batch =
                repository.claimBatch(properties.getBatchSize(), instanceId);

        if (batch.isEmpty()) {
            return;
        }

        for (OutboxEvent event : batch) {
            publish(event);
        }
    }

    private void publish(OutboxEvent event) {

        try {
            kafkaTemplate.send(
                    event.getAggregateType(),   // topic naming strategy
                    event.getAggregateId(),
                    event.getPayload()
            ).get(); // block for reliability

            repository.markPublished(event.getId());

        } catch (Exception ex) {

            int attempts = event.getPublishAttempts() + 1;

            if (attempts >= properties.getMaxAttempts()) {

                log.error("Outbox exhausted for event {}", event.getId(), ex);

                repository.markExhausted(event.getId(), ex.getMessage());

                return;
            }

            long delay = computeBackoff(attempts);

            Instant nextAttempt = Instant.now().plusMillis(delay);

            log.warn(
                    "Outbox publish failed (attempt {}), retry in {} ms for event {}",
                    attempts, delay, event.getId()
            );

            repository.markFailed(
                    event.getId(),
                    attempts,
                    nextAttempt,
                    ex.getMessage()
            );
        }
    }

    private long computeBackoff(int attempts) {

        long base = properties.getBaseBackoffMillis();

        long exp = base * (1L << (attempts - 1));

        return Math.min(exp, properties.getMaxBackoffMillis());
    }
}