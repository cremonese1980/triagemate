package com.triagemate.triage.outbox;

import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.testsupport.KafkaIntegrationTestBase;
import org.apache.kafka.clients.consumer.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.awaitility.Awaitility;

@SpringBootTest
@ActiveProfiles("test")
class OutboxHappyPathIT extends KafkaIntegrationTestBase {

    @Autowired
    private KafkaTemplate<String, Object> producer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private org.springframework.kafka.core.ConsumerFactory<String, String> consumerFactory;

    @Value("${triagemate.kafka.topics.input-received}")
    private String inputTopic;

    @Value("${triagemate.kafka.topics.decision-made}")
    private String decisionTopic;

    @Test
    void should_persist_outbox_and_publish_decision_successfully() throws Exception {

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
                null,
                null,
                input,
                Map.of()
        );

        // Act
        producer.send(inputTopic, inputId, envelope).get();

        // Assert processed_events
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

        // Assert outbox status = PUBLISHED
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {

                    String status = jdbcTemplate.queryForObject(
                            "select status from outbox_events where aggregate_id = ?",
                            String.class,
                            inputId
                    );

                    assertThat(status).isEqualTo("PUBLISHED");
                });

        // Assert decision topic receives exactly 1 message
        Consumer<String, String> consumer =
                consumerFactory.createConsumer("test-group", "test-client");

        consumer.subscribe(java.util.List.of(decisionTopic));

        var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));

        long count = 0L;
        for (var tp : records.partitions()) {
            if (tp.topic().equals(decisionTopic)) {
                count += records.records(tp).size();
            }
        }
        assertThat(count).isEqualTo(1L);

        assertThat(count).isEqualTo(1);

        consumer.close();
    }
}