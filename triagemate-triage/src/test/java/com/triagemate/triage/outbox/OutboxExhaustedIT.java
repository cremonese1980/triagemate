package com.triagemate.triage.outbox;

import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.testsupport.KafkaIntegrationTestBase;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "triagemate.outbox.max-attempts=2",
        "triagemate.outbox.poll-interval-ms=999999",
        "triagemate.outbox.initial-delay-ms=999999"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class OutboxExhaustedIT extends KafkaIntegrationTestBase {

    @MockBean
    @Qualifier("outboxKafkaTemplate")
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private org.springframework.kafka.core.KafkaTemplate<String, Object> producer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @org.springframework.beans.factory.annotation.Value("${triagemate.kafka.topics.input-received}")
    private String inputTopic;

    @Test
    void should_mark_outbox_event_as_failed_when_retry_exhausted() throws Exception {

        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        String eventId = UUID.randomUUID().toString();
        String inputId = UUID.randomUUID().toString();

        InputReceivedV1 payload = new InputReceivedV1(
                inputId,
                "email",
                "subject",
                "body",
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
                payload,
                Map.of()
        );

        producer.send(inputTopic, inputId, envelope).get();

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {

                    Integer count = jdbcTemplate.queryForObject(
                            "select count(*) from outbox_events where aggregate_id = ?",
                            Integer.class,
                            inputId
                    );

                    assertThat(count).isEqualTo(1);
                });

        // first attempt
        outboxPublisher.poll();

        jdbcTemplate.update(
                "UPDATE outbox_events SET next_attempt_at = NOW() - INTERVAL '1 second' WHERE aggregate_id = ?",
                inputId
        );

        // second attempt → should exhaust
        outboxPublisher.poll();

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {

                    Map<String, Object> row = jdbcTemplate.queryForMap(
                            "select status, publish_attempts, lock_owner, locked_until from outbox_events where aggregate_id = ?",
                            inputId
                    );

                    assertThat(row.get("status")).isEqualTo("FAILED");

                    Number attempts = (Number) row.get("publish_attempts");
                    assertThat(attempts.intValue()).isEqualTo(2);

                    assertThat(row.get("lock_owner")).isNull();
                    assertThat(row.get("locked_until")).isNull();
                });

        // wait a bit and ensure publisher doesn't retry anymore
        Thread.sleep(1000);

        Integer attemptsAfter = jdbcTemplate.queryForObject(
                "select publish_attempts from outbox_events where aggregate_id = ?",
                Integer.class,
                inputId
        );

        assertThat(attemptsAfter).isEqualTo(2);

        Mockito.verify(kafkaTemplate, Mockito.atLeastOnce()).send(any(), any(), any());
    }
}