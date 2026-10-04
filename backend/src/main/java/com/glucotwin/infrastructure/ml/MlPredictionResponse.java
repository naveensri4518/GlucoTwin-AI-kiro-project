package com.glucotwin.infrastructure.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/** JSON response from the Python ML service /predict endpoint. */
public record MlPredictionResponse(
        @JsonProperty("spike_probability") double spikeProbability,
        @JsonProperty("confidence_interval") Map<String, Double> confidenceInterval,
        @JsonProperty("top_contributing_factors") List<Map<String, Object>> topContributingFactors,
        @JsonProperty("model_version") String modelVersion,
        @JsonProperty("imputed_fields") List<String> imputedFields) {}
