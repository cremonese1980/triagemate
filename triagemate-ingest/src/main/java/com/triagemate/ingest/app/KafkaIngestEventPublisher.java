package com.triagemate.ingest.app;

import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.EventEnvelope.Producer;
import com.triagemate.contracts.events.EventEnvelope.Trace;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.ingest.app.IngestEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class KafkaIngestEventPublisher implements IngestEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaIngestEventPublisher.class);

    private static final String EVENT_TYPE = "triagemate.ingest.input-received";
    private static final int EVENT_VERSION = 1;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;
    private final String serviceName;

    public KafkaIngestEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${triagemate.kafka.topics.input-received-v1}") String topic,
            @Value("${spring.application.name}") String serviceName
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.serviceName = serviceName;
    }

    @Override
    public PublishResult publishMessageIngested(String requestId, IngestPayload payload) {

        String inputId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        var eventPayload = new InputReceivedV1(
                inputId,
                payload.channel(),
                null,                     // subject not available in fake ingest
                payload.content(),
                null,                     // from not available in fake ingest
                payload.receivedAt().toEpochMilli()
        );

        var envelope = new EventEnvelope<>(
                eventId,
                EVENT_TYPE,
                EVENT_VERSION,
                now,
                new Producer(serviceName, null),
                new Trace(requestId, requestId, null),
                eventPayload,
                Map.of()
        );

        try {
            kafkaTemplate.send(topic, inputId, envelope).get();

            log.info(
                    "Published InputReceivedV1: topic={} eventId={} inputId={} requestId={}",
                    topic, eventId, inputId, requestId
            );

            return new PublishResult(
                    UUID.fromString(inputId),
                    UUID.fromString(eventId),
                    now
            );

        } catch (Exception ex) {
            log.error(
                    "Failed to publish InputReceivedV1: topic={} eventId={} inputId={}",
                    topic, eventId, inputId,
                    ex
            );

            throw new KafkaPublishFailedException("Kafka publish failed for topic " + topic, ex);

        }
    }
}
