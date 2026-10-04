package com.glucotwin.domain.prediction;

import com.glucotwin.domain.shared.GlucoTwinException;

/** Thrown when a prediction is triggered but the twin has no glucose reading. */
public class GlucoseReadingRequiredException extends GlucoTwinException {
    public GlucoseReadingRequiredException(String patientId) {
        super("Prediction cannot run: no glucose reading in current twin state for patient: " + patientId);
    }
}
