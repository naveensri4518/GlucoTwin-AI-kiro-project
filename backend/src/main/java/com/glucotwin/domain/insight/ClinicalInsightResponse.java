package com.glucotwin.domain.insight;

import com.glucotwin.domain.prediction.ConfidenceInterval;
import com.glucotwin.domain.prediction.ContributingFactor;
import com.glucotwin.domain.prediction.RiskCategory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Structured clinical decision-support insight produced by the agent orchestration layer.
 *
 * <p>Provenance rules enforced here:
 * <ul>
 *   <li>keyObservedSignals — all OBSERVED
 *   <li>spikeProbability / riskCategory / confidenceInterval — PREDICTED
 *   <li>No SIMULATED data is ever included in a ClinicalInsightResponse
 * </ul>
 *
 * <p>This is clinical decision support — not a diagnosis or medical recommendation.
 */
public record ClinicalInsightResponse(
        UUID patientId,
        Instant generatedAt,
        int twinStateVersion,
        UUID latestPredictionId,
        RiskCategory riskCategory,
        double spikeProbability,
        ConfidenceInterval confidenceInterval,
        List<ObservedSignal> keyObservedSignals,
        List<ContributingFactor> contributingFactors,
        List<String> dataQualityWarnings,
        String evidenceSummary,
        String uncertainty,
        String dataProvenance,
        String safetyDisclaimer) {

    public static final String SAFETY_DISCLAIMER =
            "This is clinical decision support, not a diagnosis or medical recommendation. "
            + "Always apply clinical judgment. Consult the treating clinician before acting.";

    public static final String PROVENANCE_LABEL = "OBSERVED+PREDICTED";

    public ClinicalInsightResponse {
        Objects.requireNonNull(patientId);
        Objects.requireNonNull(generatedAt);
        Objects.requireNonNull(riskCategory);
        Objects.requireNonNull(evidenceSummary);
        Objects.requireNonNull(safetyDisclaimer);
        keyObservedSignals   = keyObservedSignals   != null ? List.copyOf(keyObservedSignals)   : List.of();
        contributingFactors  = contributingFactors  != null ? List.copyOf(contributingFactors)  : List.of();
        dataQualityWarnings  = dataQualityWarnings  != null ? List.copyOf(dataQualityWarnings)  : List.of();
        if (!SAFETY_DISCLAIMER.equals(safetyDisclaimer)) {
            throw new IllegalArgumentException(
                    "Safety disclaimer must match the canonical text");
        }
    }
}
