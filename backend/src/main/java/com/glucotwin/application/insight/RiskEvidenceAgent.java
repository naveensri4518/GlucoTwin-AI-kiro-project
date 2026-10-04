package com.glucotwin.application.insight;

import com.glucotwin.domain.insight.AgentResult;
import com.glucotwin.domain.insight.ClinicalKnowledgeEvidence;
import com.glucotwin.domain.insight.ClinicalKnowledgeRetriever;
import com.glucotwin.domain.insight.EvidenceAggregationResult;
import com.glucotwin.domain.insight.PredictionAnalysisResult;
import com.glucotwin.domain.insight.TwinAnalysisResult;
import com.glucotwin.domain.prediction.ContributingFactor;
import com.glucotwin.domain.twin.TwinStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Combines OBSERVED Twin signals and PREDICTED risk evidence into a structured summary,
 * and (Phase 9) retrieves supporting general clinical knowledge via
 * {@link ClinicalKnowledgeRetriever}.
 *
 * <p>Safety constraints — this agent:
 * <ul>
 *   <li>Never diagnoses disease.
 *   <li>Never prescribes medication or recommends dosage changes.
 *   <li>Never presents SIMULATED data as OBSERVED or PREDICTED.
 *   <li>Never mixes patient data with retrieved knowledge — provenance labels are kept distinct.
 *   <li>Clearly states when evidence is incomplete.
 *   <li>Does not fabricate observations or predictions.
 *   <li>If knowledge retrieval returns nothing, proceeds without failing.
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RiskEvidenceAgent {

    static final String AGENT_NAME = "RiskEvidenceAgent";

    /** Maximum number of knowledge items to attach per insight. */
    static final int MAX_KNOWLEDGE_ITEMS = 3;

    private final ClinicalKnowledgeRetriever knowledgeRetriever;

    /**
     * Aggregate evidence from twin analysis and prediction analysis,
     * and retrieve supporting general clinical knowledge for the identified risk factors.
     */
    public AgentResult<EvidenceAggregationResult> aggregate(
            TwinAnalysisResult twinResult,
            PredictionAnalysisResult predResult) {

        try {
            List<String> warnings = new ArrayList<>();
            warnings.addAll(twinResult.dataQualityWarnings());
            warnings.addAll(predResult.dataQualityWarnings());

            // Top contributing factors from the prediction (PREDICTED provenance)
            List<ContributingFactor> strongestFactors = predResult.topContributingFactors()
                    .stream()
                    .limit(5)
                    .toList();

            String evidenceSummary = buildEvidenceSummary(twinResult, predResult);
            String uncertainty     = buildUncertaintyStatement(twinResult, predResult, warnings);

            // ── Phase 9: Retrieve clinical knowledge ─────────────────────────
            // Topics are derived from contributing factor names + any observed signal names.
            // Patient-specific values are never passed to the retriever.
            List<String> topics = buildRetrievalTopics(twinResult, predResult);
            List<ClinicalKnowledgeEvidence> knowledge = List.of();
            try {
                knowledge = knowledgeRetriever.retrieve(topics, MAX_KNOWLEDGE_ITEMS);
            } catch (Exception ex) {
                // Knowledge retrieval failure must NOT block insight generation
                log.warn("[{}] Knowledge retrieval failed (non-fatal): {}", AGENT_NAME, ex.getMessage());
            }

            EvidenceAggregationResult result = new EvidenceAggregationResult(
                    predResult.riskCategory(),
                    strongestFactors,
                    evidenceSummary,
                    uncertainty,
                    warnings,
                    knowledge);

            log.debug("[{}] Evidence aggregated — risk={}, factors={}, warnings={}, knowledge={}",
                    AGENT_NAME, predResult.riskCategory(),
                    strongestFactors.size(), warnings.size(), knowledge.size());

            return AgentResult.success(result);

        } catch (Exception ex) {
            log.error("[{}] Failed to aggregate evidence", AGENT_NAME, ex);
            return AgentResult.failure(AGENT_NAME,
                    "Evidence aggregation failed: " + ex.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Derive retrieval topics from contributing factor names and observed signal names.
     * Only structural feature names are used — patient-specific values are never included.
     */
    private List<String> buildRetrievalTopics(TwinAnalysisResult twin,
                                               PredictionAnalysisResult pred) {
        List<String> topics = new ArrayList<>();
        // Factor names (e.g. "cgm_current", "hba1c_latest", "step_count_today")
        pred.topContributingFactors().stream()
                .limit(5)
                .map(ContributingFactor::factorName)
                .forEach(topics::add);
        // Observed signal names (structural, not values)
        twin.keyObservedSignals().stream()
                .map(s -> s.name())
                .limit(5)
                .forEach(topics::add);
        return List.copyOf(topics);
    }

    private String buildEvidenceSummary(TwinAnalysisResult twin,
                                        PredictionAnalysisResult pred) {
        StringBuilder sb = new StringBuilder();

        // Risk headline — PREDICTED provenance
        sb.append(String.format(
                "[PREDICTED] %s risk of glucose spike within %dh. "
                + "Spike probability: %.1f%% (95%% CI: %.1f%%–%.1f%%).",
                pred.riskCategory().name(),
                pred.predictionHorizonHours(),
                pred.spikeProbability() * 100,
                pred.confidenceInterval().low() * 100,
                pred.confidenceInterval().high() * 100));

        // Observed signals — OBSERVED provenance
        if (!twin.keyObservedSignals().isEmpty()) {
            sb.append(" [OBSERVED] Key clinical signals: ");
            twin.keyObservedSignals().forEach(s -> {
                String unit = s.unit() != null ? " " + s.unit() : "";
                sb.append(s.name()).append("=").append(s.value()).append(unit).append("; ");
            });
        }

        // Twin status context
        if (twin.twinStatus() == TwinStatus.STALE) {
            sb.append(" [OBSERVED] Note: Digital Twin data is STALE — wearable readings may be outdated.");
        } else if (twin.twinStatus() == TwinStatus.INITIALISED) {
            sb.append(" [OBSERVED] Note: Digital Twin is INITIALISED — limited wearable history available.");
        }

        // Top contributing factor — PREDICTED
        if (!pred.topContributingFactors().isEmpty()) {
            ContributingFactor top = pred.topContributingFactors().get(0);
            sb.append(String.format(
                    " [PREDICTED] Strongest model driver: %s (%s, contribution %.1f%%).",
                    top.factorName(),
                    top.direction().name(),
                    top.contribution() * 100));
        }

        return sb.toString().trim();
    }

    private String buildUncertaintyStatement(TwinAnalysisResult twin,
                                              PredictionAnalysisResult pred,
                                              List<String> warnings) {
        List<String> uncertainties = new ArrayList<>();

        double ciWidth = pred.confidenceInterval().width();
        if (ciWidth > 0.30) {
            uncertainties.add(String.format(
                    "Wide confidence interval (%.1f%%) — prediction uncertainty is elevated.",
                    ciWidth * 100));
        }

        if (!twin.hasDynamicLayer()) {
            uncertainties.add("No wearable readings available — prediction based on static EHR data only.");
        }

        if (!twin.hasStaticLayer()) {
            uncertainties.add("No EHR data uploaded — static clinical context is unavailable.");
        }

        if (twin.twinStatus() == TwinStatus.STALE) {
            uncertainties.add("Digital Twin is STALE — wearable data may not reflect current state.");
        }

        long imputedCount = warnings.stream()
                .filter(w -> w.startsWith("IMPUTED_FIELD:"))
                .count();
        if (imputedCount > 0) {
            uncertainties.add(imputedCount
                    + " feature(s) were imputed by the ML model due to missing observations.");
        }

        if (uncertainties.isEmpty()) {
            return "No significant uncertainty factors identified for this prediction.";
        }

        return String.join(" ", uncertainties);
    }
}
