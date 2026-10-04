package com.glucotwin.application.insight;

import com.glucotwin.application.ListPredictionsUseCase;
import com.glucotwin.domain.insight.AgentResult;
import com.glucotwin.domain.insight.PredictionAnalysisResult;
import com.glucotwin.domain.prediction.PredictionRecord;
import com.glucotwin.domain.prediction.PredictionStatus;
import com.glucotwin.domain.shared.PatientId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Analyzes the latest completed glucose spike prediction for a patient.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Retrieve the most recent COMPLETED prediction via {@link ListPredictionsUseCase}.
 *   <li>Report spike probability, risk category, confidence interval, model version,
 *       and top contributing factors.
 *   <li>Return an explicit failure if no completed prediction exists — never fabricate values.
 *   <li>Never generate a new prediction — that is the domain of {@code TriggerPredictionUseCase}.
 *   <li>All output provenance is PREDICTED.
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PredictionAnalysisAgent {

    static final String AGENT_NAME = "PredictionAnalysisAgent";

    private final ListPredictionsUseCase listPredictionsUseCase;

    /**
     * Analyse the latest completed prediction for {@code patientId}.
     * Returns {@link AgentResult.Success} or {@link AgentResult.Failure} — never null.
     */
    public AgentResult<PredictionAnalysisResult> analyse(PatientId patientId) {
        Page<PredictionRecord> page;
        try {
            page = listPredictionsUseCase.execute(
                    patientId, null, null,
                    PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "predictedAt")));
        } catch (Exception ex) {
            log.error("[{}] Failed to load predictions for patient {}",
                    AGENT_NAME, patientId.value(), ex);
            return AgentResult.failure(AGENT_NAME,
                    "Failed to retrieve predictions: " + ex.getMessage());
        }

        // Find the most recent COMPLETED prediction
        PredictionRecord latest = page.getContent().stream()
                .filter(p -> p.getStatus() == PredictionStatus.COMPLETED)
                .filter(p -> p.getSpikeProbability() != null)
                .filter(p -> p.getRiskCategory() != null)
                .filter(p -> p.getConfidenceInterval() != null)
                .findFirst()
                .orElse(null);

        if (latest == null) {
            log.warn("[{}] No completed prediction found for patient {}",
                    AGENT_NAME, patientId.value());
            return AgentResult.failure(AGENT_NAME,
                    "No completed prediction available for patient: " + patientId.value()
                    + ". A prediction must be generated before clinical insight can be produced.");
        }

        PredictionAnalysisResult result = new PredictionAnalysisResult(
                latest.getPredictionId().value(),
                latest.getSpikeProbability(),
                latest.getRiskCategory(),
                latest.getConfidenceInterval(),
                latest.getTopContributingFactors(),
                latest.getPredictionHorizonHours(),
                latest.getModelVersion(),
                latest.getTwinStateVersion(),
                latest.getDataQualityWarnings());

        log.debug("[{}] Completed analysis for patient {} — risk={}, prob={:.2f}",
                AGENT_NAME, patientId.value(),
                latest.getRiskCategory(), latest.getSpikeProbability());
        return AgentResult.success(result);
    }
}
