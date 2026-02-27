package com.triagemate.triage.outbox;

import com.triagemate.testsupport.KafkaIntegrationTestBase;
import com.triagemate.triage.persistence.JdbcOutboxRepository;
import com.triagemate.triage.persistence.OutboxEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that FOR UPDATE SKIP LOCKED prevents two concurrent publishers
 * from claiming the same outbox event. This is the core concurrency guarantee
 * of the outbox polling pattern.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "triagemate.outbox.poll-interval-ms=999999",
        "triagemate.outbox.initial-delay-ms=999999"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class OutboxPublisherConcurrencyIT extends KafkaIntegrationTestBase {

    @Autowired
    JdbcOutboxRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanOutbox() {
        jdbcTemplate.update("DELETE FROM outbox_events");
    }

    @Test
    void two_concurrent_claims_should_not_return_same_event() throws Exception {

        UUID id = UUID.randomUUID();

        jdbcTemplate.update("""
            INSERT INTO outbox_events
            (id, aggregate_type, aggregate_id, event_type, payload, status, created_at, next_attempt_at)
            VALUES (?, 'topic', 'key', 'type', '{}', 'PENDING', now(), now())
        """, id);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        ExecutorService exec = Executors.newFixedThreadPool(2);

        Future<List<OutboxEvent>> f1 = exec.submit(() -> {
            ready.countDown();
            go.await();
            return repository.claimBatch(10, "worker-1");
        });

        Future<List<OutboxEvent>> f2 = exec.submit(() -> {
            ready.countDown();
            go.await();
            return repository.claimBatch(10, "worker-2");
        });

        ready.await();
        go.countDown();

        List<OutboxEvent> batch1 = f1.get();
        List<OutboxEvent> batch2 = f2.get();

        int totalClaimed = batch1.size() + batch2.size();
        assertThat(totalClaimed)
                .as("exactly one worker should claim the single PENDING event")
                .isEqualTo(1);

        Integer locked = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE lock_owner IS NOT NULL", Integer.class);
        assertThat(locked).isEqualTo(1);

        exec.shutdown();
        exec.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void ten_events_should_be_claimed_without_overlap_by_concurrent_workers() throws Exception {

        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            jdbcTemplate.update("""
                INSERT INTO outbox_events
                (id, aggregate_type, aggregate_id, event_type, payload, status, created_at, next_attempt_at)
                VALUES (?, 'topic', 'key-' || ?, 'type', '{}', 'PENDING', now(), now())
            """, id, i);
        }

        int workerCount = 4;

        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch go = new CountDownLatch(1);

        ExecutorService exec = Executors.newFixedThreadPool(workerCount);

        List<Future<List<OutboxEvent>>> futures = new ArrayList<>();
        for (int w = 0; w < workerCount; w++) {
            int workerId = w;
            futures.add(exec.submit(() -> {
                ready.countDown();
                go.await();
                return repository.claimBatch(10, "worker-" + workerId);
            }));
        }

        ready.await();
        go.countDown();

        List<UUID> allClaimed = new ArrayList<>();
        for (Future<List<OutboxEvent>> f : futures) {
            for (OutboxEvent e : f.get()) {
                allClaimed.add(e.getId());
            }
        }

        assertThat(allClaimed)
                .as("all 10 events should be claimed exactly once across all workers")
                .containsExactlyInAnyOrderElementsOf(ids);

        Integer locked = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE lock_owner IS NOT NULL", Integer.class);
        assertThat(locked).isEqualTo(10);

        exec.shutdown();
        exec.awaitTermination(5, TimeUnit.SECONDS);
    }
}
