package com.glucotwin.domain.insight;

import com.glucotwin.domain.prediction.ContributingFactor;
import com.glucotwin.domain.prediction.RiskCategory;

import java.util.List;
import java.util.Objects;

/**
 * Output of the RiskEvidenceAgent.
 * Combines OBSERVED signals and PREDICTED risk into a structured evidence summary.
 * Never merges OBSERVED and PREDICTED provenance labels.
 * Never includes SIMULATED data.
 * Does not diagnose disease or prescribe medication.
 */
public record EvidenceAggregationResult(
        RiskCategory riskCategory,
        List<ContributingFactor> strongestFactors,
        String evidenceSummary,
        String uncertainty,
        List<String> combinedDataQualityWarnings) {

    public EvidenceAggregationResult {
        Objects.requireNonNull(riskCategory);
        Objects.requireNonNull(evidenceSummary);
        Objects.requireNonNull(uncertainty);
        strongestFactors             = strongestFactors             != null ? List.copyOf(strongestFactors)             : List.of();
        combinedDataQualityWarnings  = combinedDataQualityWarnings  != null ? List.copyOf(combinedDataQualityWarnings)  : List.of();
    }
}
