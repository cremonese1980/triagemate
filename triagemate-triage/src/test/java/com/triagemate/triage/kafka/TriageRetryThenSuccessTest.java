package com.triagemate.triage.kafka;

import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.testsupport.KafkaIntegrationTestBase;
import com.triagemate.triage.config.TriagemateKafkaProperties;
import com.triagemate.triage.control.routing.DecisionRouter;
import com.triagemate.triage.exception.RetrIableDecisionException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class TriageRetryThenSuccessTest extends KafkaIntegrationTestBase {

    @Autowired
    KafkaListenerEndpointRegistry registry;

    private KafkaTemplate<String, EventEnvelope<InputReceivedV1>> producer;

    @SpyBean
    private DecisionRouter decisionRouter;


    private TriagemateKafkaProperties kafkaProperties;

    @Autowired
    public TriageRetryThenSuccessTest(TriagemateKafkaProperties kafkaProperties){
        this.kafkaProperties = kafkaProperties;

    }


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
    void retriableException_retries_then_emitsSingleDecision() throws Exception {

        Consumer<String, String> consumer = decisionMadeConsumer();

        AtomicInteger invocationCounter = new AtomicInteger(0);

        doAnswer(invocation -> {
            int call = invocationCounter.incrementAndGet();
            if (call == 1) {
                throw new RetrIableDecisionException("forced transient failure");
            }
            return invocation.callRealMethod();
        }).when(decisionRouter)
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

        var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(20));

        // Deve esserci una sola decision finale
        assertThat(records.count()).isEqualTo(1);

        // Il router deve essere stato invocato due volte (retry)
        verify(decisionRouter, times(2)).route(any(), any());
    }

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

        var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(20));

        // No decision sent
        assertThat(records.count()).isEqualTo(0);

        int expectedAttempts = 1 + kafkaProperties.consumer().retry().maxRetries();
        verify(decisionRouter, times(expectedAttempts)).route(any(), any());
    }


    private Consumer<String, String> decisionMadeConsumer() {

        String groupId = "triage-retry-it-" + UUID.randomUUID();

        Map<String, Object> props =
                KafkaTestUtils.consumerProps(kafka.getBootstrapServers(), groupId, "false");

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);

        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);

        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        Consumer<String, String> consumer =
                new DefaultKafkaConsumerFactory<String, String>(props)
                        .createConsumer();

        consumer.subscribe(List.of("triagemate.triage.decision-made.v1"));

        long deadline = System.currentTimeMillis() + 10_000;
        while (consumer.assignment().isEmpty()
                && System.currentTimeMillis() < deadline) {
            consumer.poll(Duration.ofMillis(100));
        }

        return consumer;
    }
}
