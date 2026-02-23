package com.triagemate.triage.idempotency;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

@Component
public class JdbcEventIdIdempotencyGuard implements EventIdIdempotencyGuard {

    private final JdbcTemplate jdbcTemplate;

    public JdbcEventIdIdempotencyGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean tryMarkProcessed(String eventId) {

        Integer updated = jdbcTemplate.query(
                """
                INSERT INTO processed_events (event_id, processed_at)
                VALUES (?, ?)
                ON CONFLICT (event_id) DO NOTHING
                RETURNING 1
                """,
                rs -> rs.next() ? 1 : null,
                eventId,
                Timestamp.from(Instant.now())
        );

        return updated != null;
    }
}