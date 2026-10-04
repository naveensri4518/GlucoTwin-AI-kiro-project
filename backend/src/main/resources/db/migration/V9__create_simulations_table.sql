-- V9: Create simulations table for what-if scenario results.
-- IMPORTANT: Simulation results are NEVER stored in glucose_predictions.
-- This enforces data_provenance separation at the DB level (design §15.4, §16).
CREATE TABLE simulations (
    simulation_id           UUID PRIMARY KEY,
    patient_id              UUID NOT NULL REFERENCES patients(patient_id),
    twin_state_version      INTEGER NOT NULL,
    scenario_inputs         JSONB NOT NULL,
    spike_probability       NUMERIC(8,6) NOT NULL,
    risk_category           VARCHAR(20) NOT NULL,
    ci_low                  NUMERIC(8,6) NOT NULL,
    ci_high                 NUMERIC(8,6) NOT NULL,
    contributing_factors    JSONB,
    prediction_horizon_hrs  INTEGER NOT NULL DEFAULT 2,
    model_version           VARCHAR(50) NOT NULL,
    data_quality_warnings   TEXT[],
    delta_vs_baseline       NUMERIC(8,6),
    simulated_at            TIMESTAMPTZ NOT NULL,
    data_provenance         VARCHAR(20) NOT NULL DEFAULT 'SIMULATED',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_sim_probability CHECK (spike_probability >= 0.0 AND spike_probability <= 1.0),
    CONSTRAINT chk_sim_risk        CHECK (risk_category IN ('LOW','MODERATE','HIGH','CRITICAL')),
    -- Enforce SIMULATED provenance at DB level — can never be OBSERVED or PREDICTED
    CONSTRAINT chk_sim_provenance  CHECK (data_provenance = 'SIMULATED')
);

CREATE INDEX idx_simulations_patient_id   ON simulations(patient_id);
CREATE INDEX idx_simulations_simulated_at ON simulations(patient_id, simulated_at DESC);
