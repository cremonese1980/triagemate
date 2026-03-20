CREATE TABLE decisions (
    decision_id           UUID         PRIMARY KEY,
    event_id              VARCHAR(255) NOT NULL,
    policy_version        VARCHAR(50)  NOT NULL,
    outcome               VARCHAR(50)  NOT NULL,
    reason_code           VARCHAR(100),
    human_readable_reason TEXT,
    input_snapshot        JSONB        NOT NULL,
    attributes_snapshot   JSONB,
    created_at            TIMESTAMPTZ  NOT NULL,

    CONSTRAINT fk_decisions_event
        FOREIGN KEY (event_id) REFERENCES processed_events(event_id)
);

CREATE INDEX idx_decisions_event_id ON decisions(event_id);
CREATE INDEX idx_decisions_policy_version ON decisions(policy_version);
CREATE INDEX idx_decisions_created_at ON decisions(created_at);
