package com.triagemate.triage.kafka;

import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.testsupport.KafkaIntegrationTestBase;
import com.triagemate.triage.control.routing.DecisionRouter;
import com.triagemate.triage.exception.InvalidEventException;
import com.triagemate.triage.exception.RetryableDecisionException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class TriageDltIntegrationTest extends KafkaIntegrationTestBase {

    @Autowired
    KafkaListenerEndpointRegistry registry;

    @SpyBean
    private DecisionRouter decisionRouter;

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
    void nonRetryableException_sendsMessageToDlt() throws Exception {

        Consumer<String, String> dltConsumer = dltConsumer();

        // Send event with null eventId — triggers InvalidEventException (non-retryable)
        InputReceivedV1 input = validInput();

        EventEnvelope<InputReceivedV1> envelope = new EventEnvelope<>(
                null, // null eventId triggers validation failure → InvalidEventException
                "triagemate.ingest.input-received",
                1,
                Instant.now(),
                new EventEnvelope.Producer("triagemate-ingest", "it-test"),
                new EventEnvelope.Trace("req-1", "corr-1", null),
                input,
                Map.of()
        );

        producer.send("triagemate.ingest.input-received.v1", input.inputId(), envelope).get();

        // Assert: message ends up in DLT
        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    var records = KafkaTestUtils.getRecords(dltConsumer, Duration.ofSeconds(2));
                    assertThat(records.count()).isGreaterThanOrEqualTo(1);
                });

        dltConsumer.close();
    }

    @Test
    void retryExhausted_sendsMessageToDlt() throws Exception {

        Consumer<String, String> dltConsumer = dltConsumer();

        doThrow(new RetryableDecisionException("always failing"))
                .when(decisionRouter)
                .route(any(), any());

        String eventId = UUID.randomUUID().toString();
        InputReceivedV1 input = validInput();

        EventEnvelope<InputReceivedV1> envelope = new EventEnvelope<>(
                eventId,
                "triagemate.ingest.input-received",
                1,
                Instant.now(),
                new EventEnvelope.Producer("triagemate-ingest", "it-test"),
                new EventEnvelope.Trace("req-1", "corr-1", null),
                input,
                Map.of()
        );

        producer.send("triagemate.ingest.input-received.v1", input.inputId(), envelope).get();

        // Assert: after all retries exhausted, message lands in DLT
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> {
                    var records = KafkaTestUtils.getRecords(dltConsumer, Duration.ofSeconds(2));
                    assertThat(records.count()).isGreaterThanOrEqualTo(1);
                });

        dltConsumer.close();
    }

    @Test
    void dltMessage_containsExceptionHeaders() throws Exception {

        Consumer<String, String> dltConsumer = dltConsumer();

        // Send invalid event
        InputReceivedV1 input = validInput();

        EventEnvelope<InputReceivedV1> envelope = new EventEnvelope<>(
                null,
                "triagemate.ingest.input-received",
                1,
                Instant.now(),
                new EventEnvelope.Producer("triagemate-ingest", "it-test"),
                new EventEnvelope.Trace("req-1", "corr-1", null),
                input,
                Map.of()
        );

        producer.send("triagemate.ingest.input-received.v1", input.inputId(), envelope).get();

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    var records = KafkaTestUtils.getRecords(dltConsumer, Duration.ofSeconds(2));
                    assertThat(records.count()).isGreaterThanOrEqualTo(1);

                    ConsumerRecord<String, String> dltRecord = records.iterator().next();

                    // Spring Kafka adds these headers automatically
                    assertThat(dltRecord.headers().lastHeader("kafka_dlt-exception-fqcn")).isNotNull();
                    assertThat(dltRecord.headers().lastHeader("kafka_dlt-original-topic")).isNotNull();
                });

        dltConsumer.close();
    }

    private InputReceivedV1 validInput() {
        return new InputReceivedV1(
                UUID.randomUUID().toString(),
                "email",
                "subject",
                "hello world",
                "from@test",
                System.currentTimeMillis()
        );
    }

    private Consumer<String, String> dltConsumer() {
        String groupId = "triage-dlt-it-" + UUID.randomUUID();
        Map<String, Object> props =
                KafkaTestUtils.consumerProps(kafka.getBootstrapServers(), groupId, "false");

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        var consumer = new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
        consumer.subscribe(List.of("triagemate.ingest.input-received.v1.dlt"));

        long deadline = System.currentTimeMillis() + 10_000;
        while (consumer.assignment().isEmpty() && System.currentTimeMillis() < deadline) {
            consumer.poll(Duration.ofMillis(100));
        }
        return consumer;
    }
}
