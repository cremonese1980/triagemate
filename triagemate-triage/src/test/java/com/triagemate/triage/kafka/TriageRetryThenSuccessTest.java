package com.triagemate.triage.kafka;

import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.testsupport.KafkaIntegrationTestBase;
import com.triagemate.triage.control.routing.DecisionRouter;
import com.triagemate.triage.exception.RetrIableDecisionException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class TriageRetryThenSuccessTest extends KafkaIntegrationTestBase {

    @Autowired
    KafkaListenerEndpointRegistry registry;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private KafkaTemplate<String, EventEnvelope<InputReceivedV1>> producer;

    @SpyBean
    private DecisionRouter decisionRouter;

    @BeforeEach
    void setUp() {

        Map<String, Object> props = KafkaTestUtils.producerProps(kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.springframework.kafka.support.serializer.JsonSerializer.class);

        producer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));

        // wait for @KafkaListener containers to be assigned
        registry.getListenerContainers().forEach(container ->
                ContainerTestUtils.waitForAssignment(
                        container,
                        container.getContainerProperties().getTopics().length
                )
        );
    }

    /**
     * With durable idempotency "claim-first" (tryMarkProcessed before routing),
     * a retriable failure on the first attempt does NOT lead to re-execution of routing on retry,
     * because the retry attempt re-delivers the same eventId and it is immediately short-circuited as duplicate.
     *
     * This test asserts the new semantics:
     * - eventId is claimed (processed_events contains 1 row)
     * - no decision is emitted
     * - router invoked exactly once
     */
    @Test
    void retriableException_exceedsMaxRetries_noDecisionEmitted() throws Exception {

        Consumer<String, String> consumer = decisionMadeConsumer();

        doThrow(new RetrIableDecisionException("always failing"))
                .when(decisionRouter)
                .route(any(), any());

        String eventId = UUID.randomUUID().toString();
        String inputId = UUID.randomUUID().toString();

        InputReceivedV1 input = new InputReceivedV1(
                inputId,
                "email",
                "subject",
                "hello world",
                "from@test",
                System.currentTimeMillis()
        );

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

        producer.send("triagemate.ingest.input-received.v1", inputId, envelope).get();

        // Assert: eventId has been claimed in DB (durable idempotency)
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    Integer count = jdbcTemplate.queryForObject(
                            "select count(*) from processed_events where event_id = ?",
                            Integer.class,
                            eventId
                    );
                    assertThat(count).isEqualTo(1);
                });

        // Assert: no decision emitted within an observation window
        Awaitility.await()
                .during(Duration.ofSeconds(5))
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    var records = KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500));
                    assertThat(records.count()).isEqualTo(0);
                });

        // With claim-first semantics, routing is executed only once.
        verify(decisionRouter, times(1)).route(any(), any());
    }

    private Consumer<String, String> decisionMadeConsumer() {

        String groupId = "triage-retry-it-" + UUID.randomUUID();

        Map<String, Object> props =
                KafkaTestUtils.consumerProps(kafka.getBootstrapServers(), groupId, "false");

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        Consumer<String, String> consumer =
                new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();

        consumer.subscribe(List.of("triagemate.triage.decision-made.v1"));

        long deadline = System.currentTimeMillis() + 10_000;
        while (consumer.assignment().isEmpty() && System.currentTimeMillis() < deadline) {
            consumer.poll(Duration.ofMillis(100));
        }

        return consumer;
    }
}