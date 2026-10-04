package com.glucotwin.domain.prediction;

import java.util.List;
import java.util.Objects;

/** The raw output from the PredictionModelPort — before persistence. */
public record PredictionResult(
        double spikeProbability,
        ConfidenceInterval confidenceInterval,
        List<ContributingFactor> topContributingFactors,
        String modelVersion,
        List<String> imputedFields) {

    public PredictionResult {
        Objects.requireNonNull(confidenceInterval, "confidenceInterval must not be null");
        Objects.requireNonNull(modelVersion, "modelVersion must not be null");
        if (modelVersion.isBlank()) throw new IllegalArgumentException("modelVersion must not be blank");
        // Clamp probability defensively
        spikeProbability = Math.max(0.0, Math.min(1.0, spikeProbability));
        topContributingFactors = topContributingFactors == null ? List.of() : List.copyOf(topContributingFactors);
        imputedFields = imputedFields == null ? List.of() : List.copyOf(imputedFields);
    }
}
