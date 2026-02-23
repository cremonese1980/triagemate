package com.triagemate.triage.idempotency;

import com.triagemate.testsupport.KafkaIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class ConcurrentIdempotencyGuardTest extends KafkaIntegrationTestBase {

    @Autowired
    private EventIdIdempotencyGuard guard;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Test
    void only_one_thread_should_persist_same_eventId_under_race() throws Exception {

        String eventId = UUID.randomUUID().toString();

        int threads = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                ready.countDown();
                start.await();

                try {
                    transactionTemplate.executeWithoutResult(status -> {

                        if (guard.tryMarkProcessed(eventId)) {
                            successCount.incrementAndGet();
                        }

                    });
                } catch (Exception ignored) {
                    // expected possible unique constraint violation
                }

                return null;
            });
        }

        ready.await();
        start.countDown();

        executor.shutdown();
        executor.awaitTermination(threads, TimeUnit.SECONDS);

        Integer dbCount = jdbcTemplate.queryForObject(
                "select count(*) from processed_events where event_id = ?",
                Integer.class,
                eventId
        );

        assertThat(dbCount).isEqualTo(1);
        assertThat(successCount.get()).isEqualTo(1);
    }
}