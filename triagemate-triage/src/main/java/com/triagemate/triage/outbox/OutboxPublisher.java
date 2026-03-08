package com.triagemate.triage.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagemate.triage.persistence.JdbcOutboxRepository;
import com.triagemate.triage.persistence.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
    private final ObjectMapper objectMapper;
    private final String instanceId;

    public OutboxPublisher(
            JdbcOutboxRepository repository,
            @Qualifier("outboxKafkaTemplate")
            KafkaTemplate<String, String> kafkaTemplate,
            OutboxProperties properties,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.instanceId = resolveHostname() + "-" + UUID.randomUUID();
    }

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }

    @Scheduled(fixedDelayString = "${triagemate.outbox.poll-interval-ms:2000}",
            initialDelayString = "${triagemate.outbox.initial-delay-ms:2000}")
    public void poll() {

        List<OutboxEvent> batch =
                repository.claimBatch(properties.getBatchSize(), instanceId);

        if (batch.isEmpty()) {
            return;
        }

        for (OutboxEvent event : batch) {
            try {
                restoreMdcFromPayload(event);
                publish(event);
            } finally {
                MDC.clear();
            }
        }
    }

    private void publish(OutboxEvent event) {

        try {
            kafkaTemplate.send(
                    event.getAggregateType(),   // topic naming strategy
                    event.getAggregateId(),
                    event.getPayload()
            ).get(); // block for reliability — keeps MDC in scope

            repository.markPublished(event.getId());

        } catch (Exception ex) {

            int attempts = event.getPublishAttempts() + 1;

            if (attempts >= properties.getMaxAttempts()) {

                log.error("Outbox exhausted for event {}", event.getId(), ex);

                repository.markExhausted(event.getId(), attempts, ex.getMessage());

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

    /**
     * Restore MDC from outbox event payload (EventEnvelope JSON).
     * Non-critical: failure to restore doesn't prevent publish.
     */
    private void restoreMdcFromPayload(OutboxEvent event) {
        try {
            JsonNode root = objectMapper.readTree(event.getPayload());

            JsonNode trace = root.path("trace");
            if (!trace.isMissingNode()) {
                putIfPresent("requestId", trace.path("requestId"));
                putIfPresent("correlationId", trace.path("correlationId"));
            }

            putIfPresent("eventId", root.path("eventId"));

        } catch (Exception e) {
            log.warn("Failed to restore MDC from outbox payload for event {}", event.getId(), e);
        }
    }

    private static void putIfPresent(String key, JsonNode node) {
        if (!node.isMissingNode() && !node.isNull()) {
            MDC.put(key, node.asText());
        }
    }

    private long computeBackoff(int attempts) {

        long base = properties.getBaseBackoffMillis();

        long exp = base * (1L << (attempts - 1));

        return Math.min(exp, properties.getMaxBackoffMillis());
    }
}
