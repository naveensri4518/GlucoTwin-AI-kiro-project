package com.glucotwin.domain.insight;

import com.glucotwin.domain.prediction.ConfidenceInterval;
import com.glucotwin.domain.prediction.ContributingFactor;
import com.glucotwin.domain.prediction.RiskCategory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Output of the PredictionAnalysisAgent.
 * Contains only PREDICTED data from the latest completed PredictionRecord.
 * Never contains OBSERVED or SIMULATED data.
 */
public record PredictionAnalysisResult(
        UUID predictionId,
        double spikeProbability,
        RiskCategory riskCategory,
        ConfidenceInterval confidenceInterval,
        List<ContributingFactor> topContributingFactors,
        int predictionHorizonHours,
        String modelVersion,
        int twinStateVersion,
        List<String> dataQualityWarnings) {

    public static final String PROVENANCE = "PREDICTED";

    public PredictionAnalysisResult {
        Objects.requireNonNull(predictionId);
        Objects.requireNonNull(riskCategory);
        Objects.requireNonNull(confidenceInterval);
        topContributingFactors = topContributingFactors != null
                ? List.copyOf(topContributingFactors) : List.of();
        dataQualityWarnings = dataQualityWarnings != null
                ? List.copyOf(dataQualityWarnings) : List.of();
    }
}
