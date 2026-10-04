-- V6: Create dead_letter_events table
CREATE TABLE dead_letter_events (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID NOT NULL,
    patient_id      UUID,
    payload         JSONB NOT NULL,
    failure_reason  TEXT NOT NULL,
    retry_count     INTEGER NOT NULL DEFAULT 0,
    resolved        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dead_letter_unresolved ON dead_letter_events(resolved, created_at DESC);
