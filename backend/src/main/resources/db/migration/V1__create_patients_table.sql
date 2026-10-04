-- V1: Create patients table (identity anchor)
CREATE TABLE patients (
    patient_id  UUID PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
