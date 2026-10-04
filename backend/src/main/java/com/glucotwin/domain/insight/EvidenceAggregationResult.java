package com.glucotwin.domain.insight;

import com.glucotwin.domain.prediction.ContributingFactor;
import com.glucotwin.domain.prediction.RiskCategory;

import java.util.List;
import java.util.Objects;

/**
 * Output of the RiskEvidenceAgent.
 * Combines OBSERVED signals and PREDICTED risk into a structured evidence summary.
 *
 * <p>Phase 9 addition: {@code retrievedKnowledge} carries CLINICAL_KNOWLEDGE evidence
 * retrieved by the {@link ClinicalKnowledgeRetriever}. It is additive and may be empty.
 *
 * <p>Provenance invariants:
 * <ul>
 *   <li>Never merges OBSERVED and PREDICTED provenance labels.
 *   <li>Never includes SIMULATED data.
 *   <li>retrievedKnowledge items always carry CLINICAL_KNOWLEDGE provenance.
 *   <li>Does not diagnose disease or prescribe medication.
 * </ul>
 */
public record EvidenceAggregationResult(
        RiskCategory riskCategory,
        List<ContributingFactor> strongestFactors,
        String evidenceSummary,
        String uncertainty,
        List<String> combinedDataQualityWarnings,
        /** Phase 9: retrieved general clinical knowledge. Never null; may be empty. */
        List<ClinicalKnowledgeEvidence> retrievedKnowledge) {

    public EvidenceAggregationResult {
        Objects.requireNonNull(riskCategory);
        Objects.requireNonNull(evidenceSummary);
        Objects.requireNonNull(uncertainty);
        strongestFactors            = strongestFactors            != null ? List.copyOf(strongestFactors)            : List.of();
        combinedDataQualityWarnings = combinedDataQualityWarnings != null ? List.copyOf(combinedDataQualityWarnings) : List.of();
        retrievedKnowledge          = retrievedKnowledge          != null ? List.copyOf(retrievedKnowledge)          : List.of();
    }

    /** Backward-compatible constructor for Phase 8 callers with no knowledge evidence. */
    public EvidenceAggregationResult(
            RiskCategory riskCategory,
            List<ContributingFactor> strongestFactors,
            String evidenceSummary,
            String uncertainty,
            List<String> combinedDataQualityWarnings) {
        this(riskCategory, strongestFactors, evidenceSummary, uncertainty,
             combinedDataQualityWarnings, List.of());
    }
}
