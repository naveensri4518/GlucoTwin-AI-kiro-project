package com.glucotwin.application.insight;

import com.glucotwin.domain.insight.PredictionAnalysisResult;
import com.glucotwin.domain.insight.TwinAnalysisResult;
import com.glucotwin.domain.twin.TwinStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 11 — VERIFY step in the clinical insight pipeline.
 *
 * <p>Performs deterministic consistency checks after {@link RiskEvidenceAgent} aggregation
 * and before {@link ClinicalInsightResponse} construction. All checks are purely additive:
 * they append warnings to {@code dataQualityWarnings} and NEVER block the pipeline.
 *
 * <p>This agent:
 * <ul>
 *   <li>Does NOT call an LLM or any external service.
 *   <li>Does NOT modify {@code spikeProbability}, {@code riskCategory}, {@code confidenceInterval},
 *       observed values, prediction values, or any provenance labels.
 *   <li>Returns additional warnings only — the caller merges them into the existing list.
 *   <li>Never throws — all exceptions are caught and logged.
 * </ul>
 *
 * <p>Checks implemented:
 * <ol>
 *   <li>Twin version mismatch — prediction was generated against an older twin snapshot.
 *   <li>Stale Digital Twin — wearable data may not reflect current patient state.
 *   <li>Wide confidence interval — prediction uncertainty is elevated (threshold: width > 0.35).
 *   <li>Missing dynamic layer — insight is based on static EHR data only.
 * </ol>
 */
@Component
@Slf4j
public class InsightVerificationAgent {

    static final String AGENT_NAME = "InsightVerificationAgent";

    /** CI width threshold above which the "wide CI" warning is appended. */
    static final double WIDE_CI_THRESHOLD = 0.35;

    /**
     * Run all deterministic verification checks.
     *
     * @param twin the result of {@link TwinAnalysisAgent#analyse(com.glucotwin.domain.shared.PatientId)}
     * @param pred the result of {@link PredictionAnalysisAgent#analyse(com.glucotwin.domain.shared.PatientId)}
     * @return a (possibly empty) list of verification warning strings to be appended to
     *         {@code dataQualityWarnings} in the final response; never null
     */
    public List<String> verify(TwinAnalysisResult twin, PredictionAnalysisResult pred) {
        List<String> warnings = new ArrayList<>();
        try {
            checkTwinVersionMismatch(twin, pred, warnings);
            checkStaleTwin(twin, warnings);
            checkWideCi(pred, warnings);
            checkMissingDynamicLayer(twin, warnings);

            if (warnings.isEmpty()) {
                log.debug("[{}] All checks passed — no verification warnings", AGENT_NAME);
            } else {
                log.info("[{}] {} verification warning(s) appended: {}",
                        AGENT_NAME, warnings.size(), warnings);
            }
        } catch (Exception ex) {
            // Verification must NEVER block the pipeline
            log.error("[{}] Unexpected error during verification (non-fatal): {}",
                    AGENT_NAME, ex.getMessage(), ex);
        }
        return List.copyOf(warnings);
    }

    // ── Check implementations ─────────────────────────────────────────────────

    /**
     * Check 1: Twin version mismatch.
     * The prediction may have been generated against an older twin snapshot.
     */
    private void checkTwinVersionMismatch(TwinAnalysisResult twin,
                                          PredictionAnalysisResult pred,
                                          List<String> warnings) {
        if (pred.twinStateVersion() != twin.twinStateVersion()) {
            warnings.add("Prediction was generated against an earlier twin version"
                    + " (prediction twin v" + pred.twinStateVersion()
                    + ", current twin v" + twin.twinStateVersion() + ")");
        }
    }

    /**
     * Check 2: Stale Digital Twin.
     * Wearable data may not reflect the patient's current physiological state.
     */
    private void checkStaleTwin(TwinAnalysisResult twin, List<String> warnings) {
        if (twin.twinStatus() == TwinStatus.STALE) {
            warnings.add("Digital Twin data is STALE — insight confidence is reduced");
        }
    }

    /**
     * Check 3: Wide confidence interval.
     * The ML model's uncertainty for this prediction is elevated.
     */
    private void checkWideCi(PredictionAnalysisResult pred, List<String> warnings) {
        double width = pred.confidenceInterval().width();
        if (width > WIDE_CI_THRESHOLD) {
            warnings.add(String.format(
                    "Confidence interval is wide (%.1f%%) — prediction uncertainty is elevated",
                    width * 100));
        }
    }

    /**
     * Check 4: Missing dynamic layer.
     * The Digital Twin has no wearable/CGM readings; the insight relies on static EHR only.
     */
    private void checkMissingDynamicLayer(TwinAnalysisResult twin, List<String> warnings) {
        if (!twin.hasDynamicLayer()) {
            warnings.add("No wearable readings — insight based on static data only");
        }
    }
}
