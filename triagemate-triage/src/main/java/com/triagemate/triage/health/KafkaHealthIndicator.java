package com.triagemate.triage.health;

import org.apache.kafka.clients.producer.Producer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class KafkaHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(KafkaHealthIndicator.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final AtomicReference<Instant> lastSuccessfulPublish = new AtomicReference<>();

    public KafkaHealthIndicator(@Qualifier("outboxKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public Health health() {
        try (Producer<String, String> producer = kafkaTemplate.getProducerFactory().createProducer()) {
            producer.partitionsFor("__consumer_offsets");

            Instant lastPublish = lastSuccessfulPublish.get();
            return Health.up()
                    .withDetail("status", "Connected")
                    .withDetail("lastSuccessfulPublish", lastPublish != null ? lastPublish.toString() : "none")
                    .build();
        } catch (Exception ex) {
            log.warn("Kafka health check failed: {}", ex.getMessage());
            return Health.down()
                    .withDetail("status", "Disconnected")
                    .withDetail("error", ex.getMessage() != null ? ex.getMessage() : "Unknown")
                    .build();
        }
    }

    public void recordSuccessfulPublish() {
        lastSuccessfulPublish.set(Instant.now());
    }
}
