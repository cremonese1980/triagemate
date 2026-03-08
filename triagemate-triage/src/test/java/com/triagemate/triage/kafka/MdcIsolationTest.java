package com.triagemate.triage.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.testsupport.KafkaIntegrationTestBase;
import com.triagemate.testsupport.LogCaptureExtension;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import ch.qos.logback.classic.spi.ILoggingEvent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies that MDC does not leak between consecutive Kafka messages.
 * Each message must have its own isolated MDC context.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class MdcIsolationTest extends KafkaIntegrationTestBase {

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
    void kafkaConsumer_shouldNotLeakMdcBetweenMessages() throws Exception {
        String requestId1 = "req-iso-1-" + UUID.randomUUID();
        String requestId2 = "req-iso-2-" + UUID.randomUUID();

        EventEnvelope<InputReceivedV1> envelope1 = createEnvelope(
                UUID.randomUUID().toString(), requestId1, "corr-iso-1");
        EventEnvelope<InputReceivedV1> envelope2 = createEnvelope(
                UUID.randomUUID().toString(), requestId2, "corr-iso-2");

        // Send both events sequentially
        producer.send("triagemate.ingest.input-received.v1",
                envelope1.payload().inputId(), envelope1).get();
        producer.send("triagemate.ingest.input-received.v1",
                envelope2.payload().inputId(), envelope2).get();

        // Wait for both events to be processed
        await().atMost(15, TimeUnit.SECONDS).until(() -> {
            List<ILoggingEvent> logs1 = logCapture.getEventsWithMdc("requestId", requestId1);
            List<ILoggingEvent> logs2 = logCapture.getEventsWithMdc("requestId", requestId2);
            return !logs1.isEmpty() && !logs2.isEmpty();
        });

        // Verify isolation: logs for event 1 should NOT contain requestId2
        List<ILoggingEvent> logsForEvent1 = logCapture.getEventsWithMdc("requestId", requestId1);
        List<ILoggingEvent> logsForEvent2 = logCapture.getEventsWithMdc("requestId", requestId2);

        assertThat(logsForEvent1).isNotEmpty();
        assertThat(logsForEvent2).isNotEmpty();

        // No cross-contamination: event1 logs must never carry requestId2
        logsForEvent1.forEach(log ->
                assertThat(log.getMDCPropertyMap().get("requestId"))
                        .as("Event 1 log should not carry requestId from event 2")
                        .isNotEqualTo(requestId2)
        );

        // No cross-contamination: event2 logs must never carry requestId1
        logsForEvent2.forEach(log ->
                assertThat(log.getMDCPropertyMap().get("requestId"))
                        .as("Event 2 log should not carry requestId from event 1")
                        .isNotEqualTo(requestId1)
        );
    }

    private EventEnvelope<InputReceivedV1> createEnvelope(
            String eventId, String requestId, String correlationId) {

        InputReceivedV1 input = new InputReceivedV1(
                UUID.randomUUID().toString(), "email", "subject", "text",
                "from@test", 1730000000000L);

        return new EventEnvelope<>(
                eventId,
                "triagemate.ingest.input-received",
                1,
                Instant.parse("2025-01-01T00:00:00Z"),
                new EventEnvelope.Producer("triagemate-ingest", "test"),
                new EventEnvelope.Trace(requestId, correlationId, null),
                input,
                Map.of()
        );
    }
}
