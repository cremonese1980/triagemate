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
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class OutboxPublishRetryIT extends KafkaIntegrationTestBase {

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
    void should_increment_attempt_and_release_lock_on_publish_failure() throws Exception {

        // Kafka publish deve fallire
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

        // Trigger pipeline (consumer → decision → outbox write)
        producer.send(inputTopic, inputId, envelope).get();

        // Aspetta che la riga outbox venga creata
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

        // Forza esecuzione publisher (senza aspettare scheduler)
        outboxPublisher.poll();

        // Verifica retry increment + lock release
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {

                    Map<String, Object> row = jdbcTemplate.queryForMap(
                            "select status, publish_attempts, next_attempt_at, created_at, lock_owner, locked_until, last_error " +
                                    "from outbox_events where aggregate_id = ?",
                            inputId
                    );

                    assertThat(row.get("status")).isEqualTo("PENDING");

                    Number attempts = (Number) row.get("publish_attempts");
                    assertThat(attempts.intValue()).isEqualTo(1);

                    Instant created = ((java.sql.Timestamp) row.get("created_at")).toInstant();
                    Instant nextAttempt = ((java.sql.Timestamp) row.get("next_attempt_at")).toInstant();

                    assertThat(nextAttempt).isAfter(created);

                    assertThat(row.get("lock_owner")).isNull();
                    assertThat(row.get("locked_until")).isNull();

                    assertThat((String) row.get("last_error"))
                            .contains("kafka down");
                });

        Mockito.verify(kafkaTemplate).send(any(), any(), any());
    }
}