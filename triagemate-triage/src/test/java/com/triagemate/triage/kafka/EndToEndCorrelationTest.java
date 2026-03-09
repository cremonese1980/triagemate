package com.triagemate.triage.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.testsupport.KafkaIntegrationTestBase;
import com.triagemate.testsupport.LogCaptureExtension;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import ch.qos.logback.classic.spi.ILoggingEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test verifying that correlation IDs (requestId, correlationId, eventId)
 * propagate consistently from Kafka consumer through decision logic to outbox publish.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class EndToEndCorrelationTest extends KafkaIntegrationTestBase {

    @RegisterExtension
    static LogCaptureExtension logCapture = new LogCaptureExtension();

    @Autowired
    KafkaListenerEndpointRegistry registry;

    @Autowired
    ObjectMapper objectMapper;

    private KafkaTemplate<String, EventEnvelope<InputReceivedV1>> producer;

    @BeforeEach
    void setUp() {
        Map<String, Object> props = KafkaTestUtils.producerProps(kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.springframework.kafka.support.serializer.JsonSerializer.class);

        producer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));

        registry.getListenerContainers()
                .forEach(container -> ContainerTestUtils.waitForAssignment(container, 1));
    }

    @Test
    void correlation_shouldSpanFromConsumerThroughDecisionToOutbox() throws Exception {
        String requestId = "req-e2e-" + UUID.randomUUID();
        String correlationId = "corr-e2e-" + UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        String inputId = UUID.randomUUID().toString();

        InputReceivedV1 input = new InputReceivedV1(
                inputId, "email", "subject", "text", "from@test", 1730000000000L);

        EventEnvelope<InputReceivedV1> envelope = new EventEnvelope<>(
                eventId,
                "triagemate.ingest.input-received",
                1,
                Instant.parse("2025-01-01T00:00:00Z"),
                new EventEnvelope.Producer("triagemate-ingest", "test"),
                new EventEnvelope.Trace(requestId, correlationId, null),
                input,
                Map.of()
        );

        producer.send("triagemate.ingest.input-received.v1", inputId, envelope).get();

        // Wait for outbox to publish (decision-made event on output topic)
        Consumer<String, String> consumer = decisionMadeConsumer();
        var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(15));
        assertThat(records.count()).isGreaterThan(0);

        // Verify output envelope carries the original trace
        var outRecord = records.iterator().next();
        EventEnvelope<?> outEnvelope =
                objectMapper.readValue(outRecord.value(), EventEnvelope.class);

        assertThat(outEnvelope.trace()).isNotNull();
        assertThat(outEnvelope.trace().requestId()).isEqualTo(requestId);
        assertThat(outEnvelope.trace().correlationId()).isEqualTo(correlationId);
        assertThat(outEnvelope.trace().causationId()).isEqualTo(eventId);

        // Verify logs during processing had consistent MDC
        List<ILoggingEvent> correlatedLogs = logCapture.getEventsWithMdc("requestId", requestId);
        assertThat(correlatedLogs).isNotEmpty();

        correlatedLogs.forEach(log -> {
            assertThat(log.getMDCPropertyMap().get("correlationId")).isEqualTo(correlationId);
        });

        consumer.close();
    }

    private Consumer<String, String> decisionMadeConsumer() {
        String groupId = "correlation-test-" + UUID.randomUUID();

        Map<String, Object> props =
                KafkaTestUtils.consumerProps(kafka.getBootstrapServers(), groupId, "false");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        var consumer =
                new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
        consumer.subscribe(List.of("triagemate.triage.decision-made.v1"));

        long deadline = System.currentTimeMillis() + 10_000;
        while (consumer.assignment().isEmpty() && System.currentTimeMillis() < deadline) {
            consumer.poll(Duration.ofMillis(100));
        }

        return consumer;
    }
}
