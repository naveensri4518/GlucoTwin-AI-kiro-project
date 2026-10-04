-- V2: Create patient_ehr table (static EHR data)
CREATE TABLE patient_ehr (
    ehr_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id          UUID NOT NULL REFERENCES patients(patient_id),
    date_of_birth       DATE NOT NULL,
    sex                 VARCHAR(10) NOT NULL,
    bmi                 NUMERIC(5,2),
    diabetes_onset_date DATE NOT NULL,
    hba1c               NUMERIC(5,2),
    fasting_glucose     NUMERIC(5,2),
    medications         JSONB,
    lab_results         JSONB,
    data_source         VARCHAR(20) NOT NULL DEFAULT 'SYNTHETIC',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_ehr_bmi CHECK (bmi IS NULL OR (bmi >= 10.0 AND bmi <= 80.0)),
    CONSTRAINT chk_ehr_hba1c CHECK (hba1c IS NULL OR (hba1c >= 3.0 AND hba1c <= 20.0)),
    CONSTRAINT chk_ehr_fasting_glucose CHECK (fasting_glucose IS NULL OR (fasting_glucose >= 1.0 AND fasting_glucose <= 35.0)),
    CONSTRAINT chk_ehr_sex CHECK (sex IN ('MALE','FEMALE','OTHER'))
);

CREATE INDEX idx_patient_ehr_patient_id ON patient_ehr(patient_id);
