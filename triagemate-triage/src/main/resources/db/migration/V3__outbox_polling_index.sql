CREATE INDEX idx_outbox_polling
    ON outbox_events (status, next_attempt_at, created_at)
    WHERE status = 'PENDING';