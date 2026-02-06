package com.triagemate.triage.kafka;

import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@Import(TriageKafkaConsumerTest.KafkaTestConfig.class)

@ActiveProfiles("test")

class TriageKafkaConsumerTest {


    private KafkaListenerEndpointRegistry registry;

    @Autowired
    public TriageKafkaConsumerTest(KafkaListenerEndpointRegistry registry) {
        this.registry = registry;
    }

    @Container
    static KafkaContainer kafka =
            new KafkaContainer(
                    DockerImageName.parse("confluentinc/cp-kafka:7.6.1")
                            .asCompatibleSubstituteFor("confluentinc/cp-kafka")
            );


    private KafkaTemplate<String, EventEnvelope<InputReceivedV1>> kafkaTemplate;

    @BeforeEach
    void setup() {


        Map<String, Object> producerProps =
                KafkaTestUtils.producerProps(kafka.getBootstrapServers());

        producerProps.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringSerializer.class
        );
        producerProps.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.springframework.kafka.support.serializer.JsonSerializer.class
        );

        kafkaTemplate = new KafkaTemplate<>(
                new DefaultKafkaProducerFactory<>(producerProps)
        );


        // reset latch prima di ogni test
        while (TestLatches.INPUT_RECEIVED.getCount() > 0) {
            TestLatches.INPUT_RECEIVED.countDown();
        }
    }

    @DynamicPropertySource
    static void registerKafkaProps(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.kafka.bootstrap-servers",
                kafka::getBootstrapServers
        );
    }


    @Test
    void shouldConsumeInputReceivedEvent() throws Exception {
        String inputId = UUID.randomUUID().toString();

        InputReceivedV1 payload = new InputReceivedV1(
                inputId,
                "email",
                "Subject",
                "Body text",
                "from@test.com",
                System.currentTimeMillis()
        );

        EventEnvelope<InputReceivedV1> envelope =
                new EventEnvelope<>(
                        UUID.randomUUID().toString(),
                        "triagemate.ingest.input-received",
                        1,
                        Instant.now(),
                        new EventEnvelope.Producer("triagemate-ingest", "test"),
                        new EventEnvelope.Trace("req-1", "corr-1", null),
                        payload,
                        null
                );

        registry.getListenerContainers().forEach(container -> {
            ContainerTestUtils.waitForAssignment(container, 1);
        });


        kafkaTemplate.send(
                "triagemate.ingest.input-received.v1",
                inputId,
                envelope
        );



        boolean consumed =
                TestLatches.INPUT_RECEIVED.await(5, TimeUnit.SECONDS);


        assertThat(consumed)
                .as("Consumer should receive the event")
                .isTrue();
    }

    @TestConfiguration
    static class KafkaTestConfig {

        @Bean
        KafkaAdmin kafkaAdmin(
                @Value("${spring.kafka.bootstrap-servers}") String bootstrap
        ) {
            return new KafkaAdmin(
                    Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap)
            );
        }

        @Bean
        NewTopic inputReceivedTopic() {
            return new NewTopic(
                    "triagemate.ingest.input-received.v1",
                    1,
                    (short) 1
            );
        }
    }

}
