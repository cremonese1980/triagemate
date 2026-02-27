CREATE TABLE outbox_events (
   id UUID PRIMARY KEY,

   aggregate_type TEXT NOT NULL,
   aggregate_id TEXT NOT NULL,
   event_type TEXT NOT NULL,
   payload JSONB NOT NULL,

   status TEXT NOT NULL,
   publish_attempts INT NOT NULL DEFAULT 0,
   next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),

   created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
   published_at TIMESTAMPTZ NULL,

   lock_owner TEXT NULL,
   locked_until TIMESTAMPTZ NULL,

   last_error TEXT NULL
);

-- Enforce valid status values
ALTER TABLE outbox_events
    ADD CONSTRAINT chk_outbox_status
        CHECK (status IN ('PENDING','PUBLISHED','FAILED'));

-- Fast polling
CREATE INDEX idx_outbox_pending_next_attempt
    ON outbox_events (status, next_attempt_at);

-- Lock cleanup / visibility
CREATE INDEX idx_outbox_locked_until
    ON outbox_events (locked_until);

-- Aggregate tracing
CREATE INDEX idx_outbox_aggregate
    ON outbox_events (aggregate_type, aggregate_id);