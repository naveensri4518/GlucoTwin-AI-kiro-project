-- V8: Add performance indexes on glucose_predictions
CREATE INDEX idx_predictions_patient_id    ON glucose_predictions(patient_id);
CREATE INDEX idx_predictions_predicted_at  ON glucose_predictions(predicted_at DESC);
CREATE INDEX idx_predictions_model_version ON glucose_predictions(model_version);
CREATE INDEX idx_predictions_patient_time  ON glucose_predictions(patient_id, predicted_at DESC);
CREATE INDEX idx_predictions_status        ON glucose_predictions(status);
