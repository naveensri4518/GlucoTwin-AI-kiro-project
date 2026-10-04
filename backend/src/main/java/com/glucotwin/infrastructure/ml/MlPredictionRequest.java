package com.glucotwin.infrastructure.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** JSON request body sent to the Python ML service /predict endpoint. */
public record MlPredictionRequest(
        @JsonProperty("patient_id") String patientId,
        @JsonProperty("twin_version") int twinVersion,
        @JsonProperty("status") String status,
        @JsonProperty("static_layer") Map<String, Object> staticLayer,
        @JsonProperty("dynamic_layer") Map<String, Object> dynamicLayer,
        @JsonProperty("snapshot_taken_at") Instant snapshotTakenAt) {}
