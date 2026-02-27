package com.triagemate.triage.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

@Component
public class JdbcEventIdIdempotencyGuard implements EventIdIdempotencyGuard {

    private static final Logger log = LoggerFactory.getLogger(JdbcEventIdIdempotencyGuard.class);

    private final JdbcTemplate jdbcTemplate;

    public JdbcEventIdIdempotencyGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean tryMarkProcessed(String eventId) {

        Integer inserted = jdbcTemplate.query(
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

        if (inserted == null) {
            log.info("Duplicate event detected, skipping: eventId={}", eventId);
            return false;
        }
        log.debug("Event claimed for processing: eventId={}", eventId);
        return true;
    }
}