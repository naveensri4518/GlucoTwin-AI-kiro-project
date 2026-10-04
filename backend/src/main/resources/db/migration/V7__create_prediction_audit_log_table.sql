-- V7: Create prediction_audit_log table
-- patient_id_ref is SHA-256(patientId) — never the raw UUID
CREATE TABLE prediction_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    patient_id_ref  VARCHAR(64) NOT NULL,
    prediction_id   UUID,
    event_type      VARCHAR(50) NOT NULL,
    actor_role      VARCHAR(30),
    trace_id        VARCHAR(64),
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_patient_ref ON prediction_audit_log(patient_id_ref);
CREATE INDEX idx_audit_event_type ON prediction_audit_log(event_type, created_at DESC);
