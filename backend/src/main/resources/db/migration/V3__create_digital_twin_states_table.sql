-- V3: Create digital_twin_states table (one row per patient)
CREATE TABLE digital_twin_states (
    patient_id          UUID PRIMARY KEY REFERENCES patients(patient_id),
    twin_version        INTEGER NOT NULL DEFAULT 1,
    status              VARCHAR(20) NOT NULL DEFAULT 'INITIALISED',
    -- Dynamic layer (latest wearable snapshot)
    glucose_reading     NUMERIC(5,2),
    heart_rate          NUMERIC(6,2),
    hrv                 NUMERIC(6,2),
    sleep_duration      NUMERIC(4,2),
    sleep_stage         VARCHAR(10),
    step_count          INTEGER,
    activity_level      VARCHAR(20),
    wearable_event_at   TIMESTAMPTZ,
    cgm_history         JSONB,          -- rolling buffer: [{value, timestamp}]
    data_quality_flags  TEXT[],
    -- Metadata
    last_updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Optimistic locking (JPA @Version)
    version             INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT chk_twin_status CHECK (status IN ('INITIALISED','ACTIVE','STALE','ARCHIVED')),
    CONSTRAINT chk_twin_glucose CHECK (glucose_reading IS NULL OR (glucose_reading >= 1.0 AND glucose_reading <= 35.0)),
    CONSTRAINT chk_twin_hr CHECK (heart_rate IS NULL OR (heart_rate >= 20 AND heart_rate <= 300)),
    CONSTRAINT chk_twin_sleep CHECK (sleep_duration IS NULL OR (sleep_duration >= 0 AND sleep_duration <= 24))
);
