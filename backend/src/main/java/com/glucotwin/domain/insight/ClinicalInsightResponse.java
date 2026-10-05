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
 *   <li>spikeProbability / riskCategory / confidenceInterval — PREDICTED (XGBoost only)
 *   <li>clinicalKnowledgeEvidence — all CLINICAL_KNOWLEDGE (Phase 9)
 *   <li>No SIMULATED data is ever included
 * </ul>
 *
 * <p>Phase 12: {@code executionTrace} — optional observability metadata, nullable.
 * <p>Phase 13: {@code explanation} — optional LLM-generated explanation, nullable.
 *   The explanation is plain natural-language text only. It never replaces or overrides
 *   the deterministic {@code evidenceSummary}, {@code riskCategory}, or {@code spikeProbability}.
 *   It is for clinical decision support only — not a diagnosis or recommendation.
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
        String safetyDisclaimer,
        /** Phase 9: retrieved general clinical knowledge evidence. Never null; may be empty. */
        List<ClinicalKnowledgeEvidence> clinicalKnowledgeEvidence,
        /** Phase 12: in-memory pipeline execution trace. Null if trace was not captured. */
        AgentExecutionTrace executionTrace,
        /**
         * Phase 13: LLM-generated explanation paragraph. Null when LLM is disabled,
         * timed out, or failed. Never affects numeric clinical values.
         */
        String explanation) {

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
        keyObservedSignals        = keyObservedSignals        != null ? List.copyOf(keyObservedSignals)        : List.of();
        contributingFactors       = contributingFactors       != null ? List.copyOf(contributingFactors)       : List.of();
        dataQualityWarnings       = dataQualityWarnings       != null ? List.copyOf(dataQualityWarnings)       : List.of();
        clinicalKnowledgeEvidence = clinicalKnowledgeEvidence != null ? List.copyOf(clinicalKnowledgeEvidence) : List.of();
        // executionTrace and explanation are intentionally nullable — observability/LLM metadata only
        if (!SAFETY_DISCLAIMER.equals(safetyDisclaimer)) {
            throw new IllegalArgumentException(
                    "Safety disclaimer must match the canonical text");
        }
        // Enforce CLINICAL_KNOWLEDGE provenance on all knowledge items
        for (ClinicalKnowledgeEvidence item : clinicalKnowledgeEvidence) {
            if (!ClinicalKnowledgeEvidence.PROVENANCE_CLINICAL_KNOWLEDGE.equals(item.provenance())) {
                throw new IllegalArgumentException(
                        "All clinicalKnowledgeEvidence items must have provenance="
                        + ClinicalKnowledgeEvidence.PROVENANCE_CLINICAL_KNOWLEDGE);
            }
        }
    }
}
