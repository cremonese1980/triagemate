package com.triagemate.triage.kafka;

import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.testsupport.KafkaIntegrationTestBase;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class TriageMissingEventIdTest extends KafkaIntegrationTestBase {

    @Autowired
    KafkaListenerEndpointRegistry registry;

    private KafkaTemplate<String, EventEnvelope<InputReceivedV1>> producer;

    @BeforeEach
    void setUp() {
        Map<String, Object> props = KafkaTestUtils.producerProps(kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.springframework.kafka.support.serializer.JsonSerializer.class);

        producer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));

        registry.getListenerContainers().forEach(container ->
                ContainerTestUtils.waitForAssignment(
                        container,
                        container.getContainerProperties().getTopics().length
                )
        );
    }

    @Test
    void missingEventId_validationFails_noDecisionEmitted() throws Exception {

        Consumer<String, String> consumer = decisionMadeConsumer();

        InputReceivedV1 input = new InputReceivedV1(
                UUID.randomUUID().toString(),
                "email",
                "subject",
                "hello world",
                "from@test",
                System.currentTimeMillis()
        );

        EventEnvelope<InputReceivedV1> envelope = new EventEnvelope<>(
                null, // ← INVALID eventId
                "triagemate.ingest.input-received",
                1,
                Instant.now(),
                new EventEnvelope.Producer("triagemate-ingest", "it-test"),
                new EventEnvelope.Trace("req-1", "corr-1", null),
                input,
                Map.of()
        );

        producer.send("triagemate.ingest.input-received.v1",
                input.inputId(), envelope).get();

        var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));

        // No decision emitted for null eventId
        assertThat(records.count()).isEqualTo(0);

        envelope = new EventEnvelope<>(
                "", // ← INVALID eventId
                "triagemate.ingest.input-received",
                1,
                Instant.now(),
                new EventEnvelope.Producer("triagemate-ingest", "it-test"),
                new EventEnvelope.Trace("req-1", "corr-1", null),
                input,
                Map.of()
        );

        producer.send("triagemate.ingest.input-received.v1",
                input.inputId(), envelope).get();

        records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));

        // No decision emitted for empty eventId
        assertThat(records.count()).isEqualTo(0);

        // No retry loop — implicitly verified: no duplicate decisions arrive within 5s
    }

    private Consumer<String, String> decisionMadeConsumer() {
        String groupId = "triage-invalid-it-" + UUID.randomUUID();
        Map<String, Object> props =
                KafkaTestUtils.consumerProps(kafka.getBootstrapServers(), groupId, "false");

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        var consumer = new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
        consumer.subscribe(List.of("triagemate.triage.decision-made.v1"));

        long deadline = System.currentTimeMillis() + 10_000;
        while (consumer.assignment().isEmpty() && System.currentTimeMillis() < deadline) {
            consumer.poll(Duration.ofMillis(100));
        }
        return consumer;
    }
}
