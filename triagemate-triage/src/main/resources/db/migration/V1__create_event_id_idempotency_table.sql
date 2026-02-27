CREATE TABLE processed_events (
    id           BIGSERIAL    PRIMARY KEY,
    event_id     VARCHAR(255) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE processed_events
    ADD CONSTRAINT uk_event_id UNIQUE (event_id);
