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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "triagemate.outbox.poll-interval-ms=999999",
        "triagemate.outbox.initial-delay-ms=999999"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class JdbcOutboxRepositoryLockingIT extends KafkaIntegrationTestBase {

    @Autowired
    JdbcOutboxRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanOutbox() {
        jdbcTemplate.update("DELETE FROM outbox_events");
    }

    @Test
    void should_allow_only_one_claim_for_same_record() {

        UUID id = UUID.randomUUID();

        jdbcTemplate.update("""
            insert into outbox_events
            (id, aggregate_type, aggregate_id, event_type, payload, status, created_at, next_attempt_at)
            values (?, 'topic', 'key', 'type', '{}', 'PENDING', now(), now())
        """, id);

        List<OutboxEvent> first = repository.claimBatch(10, "worker-1");
        List<OutboxEvent> second = repository.claimBatch(10, "worker-2");

        assertThat(first).hasSize(1);
        assertThat(second).isEmpty();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select lock_owner, locked_until from outbox_events where id = ?", id);

        assertThat(row.get("lock_owner")).isNotNull();
        assertThat(row.get("locked_until")).isNotNull();
    }

    @Test
    void should_reclaim_when_lock_expired() {

        UUID id = UUID.randomUUID();

        jdbcTemplate.update("""
            insert into outbox_events
            (id, aggregate_type, aggregate_id, event_type, payload, status, created_at, next_attempt_at)
            values (?, 'topic', 'key', 'type', '{}', 'PENDING', now(), now())
        """, id);

        repository.claimBatch(10, "worker-1");

        jdbcTemplate.update("""
            update outbox_events
            set locked_until = now() - interval '1 second'
            where id = ?
        """, id);

        List<OutboxEvent> reclaimed = repository.claimBatch(10, "worker-2");

        assertThat(reclaimed).hasSize(1);
        assertThat(reclaimed.get(0).getLockOwner()).isEqualTo("worker-2");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select lock_owner, locked_until from outbox_events where id = ?", id);

        assertThat(row.get("lock_owner")).isEqualTo("worker-2");
        assertThat(row.get("locked_until")).isNotNull();
    }
}
