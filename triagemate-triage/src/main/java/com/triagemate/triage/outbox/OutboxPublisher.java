package com.triagemate.triage.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagemate.triage.health.KafkaHealthIndicator;
import com.triagemate.triage.observability.OutboxMetrics;
import com.triagemate.triage.persistence.JdbcOutboxRepository;
import com.triagemate.triage.persistence.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class OutboxPublisher implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final JdbcOutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;
    private final ObjectMapper objectMapper;
    private final OutboxMetrics metrics;
    private final KafkaHealthIndicator kafkaHealth;
    private final String instanceId;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean batchInFlight = new AtomicBoolean(false);
    private final AtomicReference<CountDownLatch> batchComplete = new AtomicReference<>(new CountDownLatch(0));

    public OutboxPublisher(
            JdbcOutboxRepository repository,
            @Qualifier("outboxKafkaTemplate")
            KafkaTemplate<String, String> kafkaTemplate,
            OutboxProperties properties,
            ObjectMapper objectMapper,
            OutboxMetrics metrics,
            KafkaHealthIndicator kafkaHealth
    ) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.kafkaHealth = kafkaHealth;
        this.instanceId = resolveHostname() + "-" + UUID.randomUUID();
    }

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }

    @Override
    public void start() {
        running.set(true);
        log.info("OutboxPublisher started, instanceId={}", instanceId);
    }

    @Override
    public void stop() {
        stop(() -> {});
    }

    @Override
    public void stop(Runnable callback) {
        log.info("OutboxPublisher shutting down, waiting for in-flight batch...");
        running.set(false);

        if (batchInFlight.get()) {
            try {
                boolean completed = batchComplete.get().await(properties.getLockDurationSeconds(), TimeUnit.SECONDS);
                if (!completed) {
                    log.warn("OutboxPublisher shutdown timed out waiting for in-flight batch");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("OutboxPublisher shutdown interrupted");
            }
        }

        log.info("OutboxPublisher stopped");
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    @Scheduled(fixedDelayString = "${triagemate.outbox.poll-interval-ms:2000}",
            initialDelayString = "${triagemate.outbox.initial-delay-ms:2000}")
    public void poll() {

        if (!running.get()) {
            return;
        }

        List<OutboxEvent> batch =
                repository.claimBatch(properties.getBatchSize(), instanceId);

        if (batch.isEmpty()) {
            return;
        }

        batchInFlight.set(true);
        CountDownLatch latch = new CountDownLatch(1);
        batchComplete.set(latch);
        try {
            for (OutboxEvent event : batch) {
                try {
                    restoreMdcFromPayload(event);
                    publish(event);
                } finally {
                    MDC.clear();
                }
            }
        } finally {
            batchInFlight.set(false);
            latch.countDown();
        }
    }

    private void publish(OutboxEvent event) {

        if (event.getPayload() == null || event.getPayload().isBlank()) {
            metrics.recordValidationFailure();
            log.error("Outbox event {} has null/empty payload, skipping", event.getId());
            repository.markExhausted(event.getId(), event.getPublishAttempts(), "Empty payload");
            return;
        }

        try {
            kafkaTemplate.send(
                    event.getAggregateType(),
                    event.getAggregateId(),
                    event.getPayload()
            ).get();

            repository.markPublished(event.getId());
            metrics.recordPublished();
            kafkaHealth.recordSuccessfulPublish();

        } catch (Exception ex) {

            metrics.recordKafkaFailure();

            int attempts = event.getPublishAttempts() + 1;

            if (attempts >= properties.getMaxAttempts()) {

                log.error("Outbox exhausted for event {}", event.getId(), ex);

                repository.markExhausted(event.getId(), attempts, ex.getMessage());

                return;
            }

            metrics.recordRetry();

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
