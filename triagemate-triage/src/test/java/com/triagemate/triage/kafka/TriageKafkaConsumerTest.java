package com.triagemate.triage.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.DecisionMadeV1;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class TriageKafkaConsumerTest {

    @Container
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1")
                    .asCompatibleSubstituteFor("confluentinc/cp-kafka"));

    @Autowired
    ObjectMapper objectMapper;

    private KafkaTemplate<String, EventEnvelope<InputReceivedV1>> producer;

    @BeforeEach
    void setUp() {
        Map<String, Object> props = KafkaTestUtils.producerProps(kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, org.springframework.kafka.support.serializer.JsonSerializer.class);
        producer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        r.add("triagemate.kafka.topics.input-received", () -> "triagemate.ingest.input-received.v1");
        r.add("triagemate.kafka.topics.decision-made", () -> "triagemate.triage.decision-made.v1");
    }

    @Test
    void consumedInputEventEmitsDecisionMadeEvent() throws Exception {
        Consumer<String, String> consumer = decisionMadeConsumer();

        InputReceivedV1 input = new InputReceivedV1("input-123", "email", "subject", "text", "from@test", 1730000000000L);
        EventEnvelope<InputReceivedV1> envelope = new EventEnvelope<>(
                "event-123",
                "triagemate.ingest.input-received",
                1,
                Instant.parse("2025-01-01T00:00:00Z"),
                new EventEnvelope.Producer("triagemate-ingest", "test"),
                new EventEnvelope.Trace("req-1", "corr-1", null),
                input,
                Map.of()
        );

        producer.send("triagemate.ingest.input-received.v1", input.inputId(), envelope).get();

        var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        assertThat(records.count()).isGreaterThan(0);

        var decisionRecord = records.iterator().next();
        EventEnvelope<?> decisionEnvelope = objectMapper.readValue(decisionRecord.value(), EventEnvelope.class);
        DecisionMadeV1 decisionMade = objectMapper.convertValue(decisionEnvelope.payload(), DecisionMadeV1.class);

        assertThat(decisionEnvelope.eventType()).isEqualTo("triagemate.triage.decision-made");
        assertThat(decisionEnvelope.eventVersion()).isEqualTo(1);
        assertThat(decisionMade.inputId()).isEqualTo("input-123");
    }


    @Test
    void duplicateEventIdDoesNotEmitDuplicateDecision() throws Exception {
        Consumer<String, String> consumer = decisionMadeConsumer();

        InputReceivedV1 input = new InputReceivedV1("input-dup", "email", "subject", "text", "from@test", 1730000000000L);
        EventEnvelope<InputReceivedV1> envelope = new EventEnvelope<>(
                "event-dup",
                "triagemate.ingest.input-received",
                1,
                Instant.parse("2025-01-01T00:00:00Z"),
                new EventEnvelope.Producer("triagemate-ingest", "test"),
                new EventEnvelope.Trace("req-2", "corr-2", null),
                input,
                Map.of()
        );

        producer.send("triagemate.ingest.input-received.v1", input.inputId(), envelope).get();
        producer.send("triagemate.ingest.input-received.v1", input.inputId(), envelope).get();

        var firstPoll = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        var secondPoll = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2));

        assertThat(firstPoll.count()).isEqualTo(1);
        assertThat(secondPoll.count()).isEqualTo(0);
    }

    private Consumer<String, String> decisionMadeConsumer() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(kafka.getBootstrapServers(), "triage-test-consumer", "false");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        var consumer = new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
        consumer.subscribe(java.util.List.of("triagemate.triage.decision-made.v1"));
        return consumer;
    }
}
