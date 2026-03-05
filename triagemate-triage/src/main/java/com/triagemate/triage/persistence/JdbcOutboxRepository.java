package com.triagemate.triage.persistence;

import com.triagemate.triage.outbox.OutboxProperties;
import com.triagemate.triage.outbox.OutboxStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JDBC-based repository for outbox event mutations.
 *
 * JPA ({@link OutboxEventRepository}) is used only for the atomic INSERT inside the
 * business transaction. All polling, claiming, and status updates use raw JDBC because:
 * - {@code FOR UPDATE SKIP LOCKED} requires native SQL
 * - {@code RETURNING} clause is not supported by JPA
 * - Lock semantics must be explicit, not managed by Hibernate dirty-checking
 */
@Repository
public class JdbcOutboxRepository {

    private final JdbcTemplate jdbcTemplate;
    private final OutboxProperties properties;

    public JdbcOutboxRepository(JdbcTemplate jdbcTemplate, OutboxProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public List<OutboxEvent> claimBatch(int batchSize, String instanceId) {

        String sql = """
    WITH cte AS (
        SELECT id
        FROM outbox_events
        WHERE status = ?
          AND next_attempt_at <= now()
        ORDER BY created_at
        LIMIT ?
        FOR UPDATE SKIP LOCKED
    )
    UPDATE outbox_events o
    SET
        lock_owner = ?,
        locked_until = now() + make_interval(secs => ?)
    FROM cte
    WHERE o.id = cte.id
    RETURNING o.*;
    """;

        return jdbcTemplate.query(
                sql,
                ps -> {
                    ps.setString(1, OutboxStatus.PENDING.name());
                    ps.setInt(2, batchSize);
                    ps.setString(3, instanceId);
                    ps.setLong(4, properties.getLockDurationSeconds());
                },
                new OutboxRowMapper()
        );
    }

    public void markPublished(UUID id) {

        String sql = """
        UPDATE outbox_events
        SET
            status = ?,
            published_at = now(),
            lock_owner = null,
            locked_until = null
        WHERE id = ?
        """;

        jdbcTemplate.update(
                sql,
                OutboxStatus.PUBLISHED.name(),
                id
        );
    }

    public void markFailed(
            UUID id,
            int publishAttempts,
            Instant nextAttemptAt,
            String error
    ) {

        String sql = """
        UPDATE outbox_events
        SET
            publish_attempts = ?,
            next_attempt_at = ?,
            lock_owner = null,
            locked_until = null,
            last_error = ?
        WHERE id = ?
          AND status = ?
        """;

        jdbcTemplate.update(
                sql,
                publishAttempts,
                Timestamp.from(nextAttemptAt),
                truncate(error),
                id,
                OutboxStatus.PENDING.name()
        );
    }

    public void markExhausted(UUID id, int publishAttempts, String error) {

        String sql = """
        UPDATE outbox_events
        SET
            status = ?,
            publish_attempts = ?,
            lock_owner = null,
            locked_until = null,
            last_error = ?
        WHERE id = ?
        """;

        jdbcTemplate.update(
                sql,
                OutboxStatus.FAILED.name(),
                publishAttempts,
                truncate(error),
                id
        );
    }

    private String truncate(String error) {
        if (error == null) return null;
        int max = 1000;
        return error.length() <= max ? error : error.substring(0, max);
    }

    private static class OutboxRowMapper implements RowMapper<OutboxEvent> {

        @Override
        public OutboxEvent mapRow(ResultSet rs, int rowNum) throws SQLException {

            UUID id = rs.getObject("id", UUID.class);

            OutboxEvent event = new OutboxEvent(
                    id,
                    rs.getString("aggregate_type"),
                    rs.getString("aggregate_id"),
                    rs.getString("event_type"),
                    rs.getString("payload"),
                    OutboxStatus.valueOf(rs.getString("status")),
                    rs.getTimestamp("created_at").toInstant()
            );

            event.setPublishAttempts(rs.getInt("publish_attempts"));
            event.setNextAttemptAt(rs.getTimestamp("next_attempt_at").toInstant());
            event.setPublishedAt(toInstant(rs, "published_at"));
            event.setLockOwner(rs.getString("lock_owner"));
            event.setLockedUntil(toInstant(rs, "locked_until"));
            event.setLastError(rs.getString("last_error"));

            return event;
        }

        private Instant toInstant(ResultSet rs, String column) throws SQLException {
            var ts = rs.getTimestamp(column);
            return ts != null ? ts.toInstant() : null;
        }
    }
}
