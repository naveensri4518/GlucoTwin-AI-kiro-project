package com.glucotwin.domain.prediction;

import com.glucotwin.domain.twin.TwinStateSnapshot;

/**
 * Domain port for the ML prediction model.
 * Implementations: MlServicePredictionModel (production), MockPredictionModel (tests).
 * Swapping implementations requires only a configuration/wiring change.
 */
public interface PredictionModelPort {

    /**
     * Predict spike probability for the given twin state snapshot.
     *
     * @param snapshot immutable snapshot of the Digital Twin state
     * @return prediction result with probability, CI, contributing factors, and model version
     * @throws PredictionServiceUnavailableException if the ML service is unreachable
     * @throws PredictionTimeoutException if the ML service does not respond in time
     */
    PredictionResult predict(TwinStateSnapshot snapshot);

    /** Returns the version string of the model artefact currently in use. */
    String getModelVersion();
}
