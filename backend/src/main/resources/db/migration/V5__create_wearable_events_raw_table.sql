-- V5: Create wearable_events_raw table (audit trail of all events)
CREATE TABLE wearable_events_raw (
    event_id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id          UUID NOT NULL REFERENCES patients(patient_id),
    glucose_reading     NUMERIC(5,2) NOT NULL,
    heart_rate          NUMERIC(6,2),
    hrv                 NUMERIC(6,2),
    sleep_duration      NUMERIC(4,2),
    sleep_stage         VARCHAR(10),
    step_count          INTEGER,
    activity_level      VARCHAR(20),
    event_timestamp     TIMESTAMPTZ NOT NULL,
    received_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    processing_status   VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    data_source         VARCHAR(20) NOT NULL DEFAULT 'SYNTHETIC',
    CONSTRAINT chk_we_glucose CHECK (glucose_reading >= 1.0 AND glucose_reading <= 35.0),
    CONSTRAINT chk_we_hr CHECK (heart_rate IS NULL OR (heart_rate >= 20 AND heart_rate <= 300)),
    CONSTRAINT chk_we_proc_status CHECK (processing_status IN ('RECEIVED','PROCESSED','DEAD_LETTERED'))
);

CREATE INDEX idx_wearable_events_patient_id ON wearable_events_raw(patient_id);
CREATE INDEX idx_wearable_events_timestamp ON wearable_events_raw(event_timestamp DESC);
