package com.triagemate.triage.idempotency;

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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class ConcurrentKafkaIdempotencyIT extends KafkaIntegrationTestBase {

    private static final String INPUT_TOPIC =
            "triagemate.ingest.input-received.v1";

    @Autowired
    private KafkaTemplate<String, EventEnvelope<InputReceivedV1>> kafkaTemplate;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Test
    void should_allow_only_one_thread_to_claim_same_eventId_concurrently() throws Exception {

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
                        "triagemate.ingest.input-received.v1",
                        1,
                        Instant.EPOCH,
                        null,
                        trace,
                        payload,
                        Map.of()
                );

        int threads = 32;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    kafkaTemplate.send(INPUT_TOPIC, eventId, envelope).get();
                } catch (Exception ignored) {
                }
            });
        }

        ready.await();
        start.countDown();

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

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
    }
}