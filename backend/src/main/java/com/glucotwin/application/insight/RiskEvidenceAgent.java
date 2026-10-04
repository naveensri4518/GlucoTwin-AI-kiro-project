package com.glucotwin.application.insight;

import com.glucotwin.domain.insight.AgentResult;
import com.glucotwin.domain.insight.EvidenceAggregationResult;
import com.glucotwin.domain.insight.PredictionAnalysisResult;
import com.glucotwin.domain.insight.TwinAnalysisResult;
import com.glucotwin.domain.prediction.ContributingFactor;
import com.glucotwin.domain.prediction.RiskCategory;
import com.glucotwin.domain.twin.TwinStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Combines OBSERVED Twin signals and PREDICTED risk evidence into a structured summary.
 *
 * <p>Safety constraints — this agent:
 * <ul>
 *   <li>Never diagnoses disease.
 *   <li>Never prescribes medication or recommends dosage changes.
 *   <li>Never presents SIMULATED data as OBSERVED or PREDICTED.
 *   <li>Clearly states when evidence is incomplete.
 *   <li>Does not fabricate observations or predictions.
 * </ul>
 */
@Component
@Slf4j
public class RiskEvidenceAgent {

    static final String AGENT_NAME = "RiskEvidenceAgent";

    /**
     * Aggregate evidence from twin analysis and prediction analysis.
     * Both inputs are required; returns failure if either is absent.
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

            EvidenceAggregationResult result = new EvidenceAggregationResult(
                    predResult.riskCategory(),
                    strongestFactors,
                    evidenceSummary,
                    uncertainty,
                    warnings);

            log.debug("[{}] Evidence aggregated — risk={}, factors={}, warnings={}",
                    AGENT_NAME, predResult.riskCategory(),
                    strongestFactors.size(), warnings.size());

            return AgentResult.success(result);

        } catch (Exception ex) {
            log.error("[{}] Failed to aggregate evidence", AGENT_NAME, ex);
            return AgentResult.failure(AGENT_NAME,
                    "Evidence aggregation failed: " + ex.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

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
