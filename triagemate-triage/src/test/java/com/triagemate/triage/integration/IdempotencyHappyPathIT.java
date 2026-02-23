package com.triagemate.triage.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.testsupport.KafkaIntegrationTestBase;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class IdempotencyHappyPathIT  extends KafkaIntegrationTestBase{

    @Autowired
    private KafkaTemplate<String, EventEnvelope<?>> kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String INPUT_TOPIC = "triagemate.ingest.input-received.v1";
    private static final String OUTPUT_TOPIC = "triagemate.triage.decision-made.v1";

    @Test
    void should_process_valid_event_and_mark_db() throws Exception {

        String eventId = UUID.randomUUID().toString();


        InputReceivedV1 payload = new InputReceivedV1("input-1", "email", "subject", "text", "from", 123L);
        EventEnvelope.Trace trace = new EventEnvelope.Trace("request-1", "corr-1", "cause-1");
        EventEnvelope<InputReceivedV1> envelope = new EventEnvelope<>(
                eventId,
                "triagemate.ingest.input-received",
                1,
                Instant.EPOCH,
                null,
                trace,
                payload,
                Map.of()
        );

        kafkaTemplate.send(INPUT_TOPIC, eventId, envelope);

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

        Integer total = jdbcTemplate.queryForObject(
                "select count(*) from processed_events where event_id = ?",
                Integer.class,
                eventId
        );

        assertThat(total).isEqualTo(1);
    }

    @Test
    void should_ignore_duplicate_event_same_runtime() throws Exception {

        String eventId = UUID.randomUUID().toString();

        InputReceivedV1 payload = new InputReceivedV1(
                "input-1",
                "email",
                "subject",
                "text",
                "from",
                123L
        );

        EventEnvelope.Trace trace =
                new EventEnvelope.Trace("request-1", "corr-1", "cause-1");

        EventEnvelope<InputReceivedV1> envelope =
                new EventEnvelope<>(
                        eventId,
                        "triagemate.ingest.input-received",
                        1,
                        Instant.EPOCH,
                        null,
                        trace,
                        payload,
                        Map.of()
                );

        // Send first time
        kafkaTemplate.send(INPUT_TOPIC, eventId, envelope);

        // Send duplicate
        kafkaTemplate.send(INPUT_TOPIC, eventId, envelope);

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

        Integer finalCount = jdbcTemplate.queryForObject(
                "select count(*) from processed_events where event_id = ?",
                Integer.class,
                eventId
        );

        assertThat(finalCount).isEqualTo(1);
    }
}