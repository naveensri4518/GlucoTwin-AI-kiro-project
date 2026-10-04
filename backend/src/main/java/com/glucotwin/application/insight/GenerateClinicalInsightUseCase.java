package com.glucotwin.application.insight;

import com.glucotwin.domain.insight.AgentResult;
import com.glucotwin.domain.insight.ClinicalInsightResponse;
import com.glucotwin.domain.insight.EvidenceAggregationResult;
import com.glucotwin.domain.insight.InsightGenerationException;
import com.glucotwin.domain.insight.PredictionAnalysisResult;
import com.glucotwin.domain.insight.TwinAnalysisResult;
import com.glucotwin.domain.shared.PatientId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent Supervisor — orchestrates the full Phase 11 clinical insight pipeline.
 *
 * <p>Pipeline (Phase 11):
 * <ol>
 *   <li>{@link TwinAnalysisAgent}         — reads OBSERVED twin state (read-only)
 *   <li>{@link PredictionAnalysisAgent}   — reads latest PREDICTED risk (read-only)
 *   <li>{@link RiskEvidenceAgent}         — aggregates evidence + retrieves CLINICAL_KNOWLEDGE
 *   <li>{@link InsightVerificationAgent}  — deterministic consistency checks (non-blocking)
 *   <li>Assemble {@link ClinicalInsightResponse} with all warnings + safety disclaimer
 *   <li>{@link InsightAuditWriter}        — durable audit in its own REQUIRES_NEW transaction
 * </ol>
 *
 * <p>Transaction strategy:
 * <ul>
 *   <li>This method runs {@code @Transactional(readOnly=true)} — the entire data-reading
 *       pipeline is read-only and never mutates the Digital Twin, prediction, or simulation.
 *   <li>{@link InsightAuditWriter#write} runs in a separate {@code REQUIRES_NEW}
 *       transaction, isolating the audit write from the read-only pipeline.
 *   <li>Audit failure is non-fatal — the insight response is always returned.
 * </ul>
 *
 * <p>Safety invariants:
 * <ul>
 *   <li>Twin unavailable → throws {@link InsightGenerationException}, never fabricates.
 *   <li>Prediction unavailable → throws with "generate prediction first" message.
 *   <li>SIMULATED data is never included in the response.
 *   <li>OBSERVED, PREDICTED, and CLINICAL_KNOWLEDGE provenance labels always kept distinct.
 *   <li>Digital Twin is never mutated.
 *   <li>Safety disclaimer always present verbatim.
 *   <li>Verification warnings are additive only — never block the pipeline.
 *   <li>Audit failure never blocks the clinical insight response.
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GenerateClinicalInsightUseCase {

    private final TwinAnalysisAgent         twinAgent;
    private final PredictionAnalysisAgent   predictionAgent;
    private final RiskEvidenceAgent         evidenceAgent;
    private final InsightVerificationAgent  verificationAgent;
    private final InsightAuditWriter        auditWriter;

    /**
     * Generate a structured clinical insight for the given patient.
     *
     * @param patientId the patient to analyse
     * @param question  the clinician's question (logged for context; does not alter data)
     * @return a fully populated {@link ClinicalInsightResponse}
     * @throws InsightGenerationException if required evidence is unavailable
     */
    @Transactional(readOnly = true)
    public ClinicalInsightResponse execute(PatientId patientId, String question) {
        log.info("[AgentSupervisor] Starting clinical insight for patient={} question='{}'",
                patientId.value(), question);

        // ── Step 1: Twin Analysis Agent ───────────────────────────────────────
        AgentResult<TwinAnalysisResult> twinResult = twinAgent.analyse(patientId);
        if (!twinResult.isSuccess()) {
            AgentResult.Failure<TwinAnalysisResult> f = (AgentResult.Failure<TwinAnalysisResult>) twinResult;
            log.warn("[AgentSupervisor] TwinAnalysisAgent failed: {}", f.reason());
            throw InsightGenerationException.twinUnavailable(patientId.value().toString());
        }
        TwinAnalysisResult twin = twinResult.valueOrThrow();

        // ── Step 2: Prediction Analysis Agent ────────────────────────────────
        AgentResult<PredictionAnalysisResult> predResult = predictionAgent.analyse(patientId);
        if (!predResult.isSuccess()) {
            AgentResult.Failure<PredictionAnalysisResult> f =
                    (AgentResult.Failure<PredictionAnalysisResult>) predResult;
            log.warn("[AgentSupervisor] PredictionAnalysisAgent failed: {}", f.reason());
            throw InsightGenerationException.predictionUnavailable(patientId.value().toString());
        }
        PredictionAnalysisResult pred = predResult.valueOrThrow();

        // ── Step 3: Risk/Evidence Agent (+ knowledge retrieval) ───────────────
        AgentResult<EvidenceAggregationResult> evidenceResult =
                evidenceAgent.aggregate(twin, pred);
        if (!evidenceResult.isSuccess()) {
            AgentResult.Failure<EvidenceAggregationResult> f =
                    (AgentResult.Failure<EvidenceAggregationResult>) evidenceResult;
            log.warn("[AgentSupervisor] RiskEvidenceAgent failed: {}", f.reason());
            throw InsightGenerationException.agentFailure("RiskEvidenceAgent", f.reason());
        }
        EvidenceAggregationResult evidence = evidenceResult.valueOrThrow();

        // ── Step 4: Verification Agent (non-blocking consistency checks) ──────
        List<String> verificationWarnings = verificationAgent.verify(twin, pred);

        // ── Step 5: Assemble final response ───────────────────────────────────
        // Merge evidence warnings + verification warnings — verification is additive only
        List<String> allWarnings = new ArrayList<>(evidence.combinedDataQualityWarnings());
        allWarnings.addAll(verificationWarnings);

        ClinicalInsightResponse response = new ClinicalInsightResponse(
                patientId.value(),
                Instant.now(),
                twin.twinStateVersion(),
                pred.predictionId(),
                pred.riskCategory(),
                pred.spikeProbability(),
                pred.confidenceInterval(),
                twin.keyObservedSignals(),
                evidence.strongestFactors(),
                allWarnings,
                evidence.evidenceSummary(),
                evidence.uncertainty(),
                ClinicalInsightResponse.PROVENANCE_LABEL,
                ClinicalInsightResponse.SAFETY_DISCLAIMER,
                evidence.retrievedKnowledge());

        log.info("[AgentSupervisor] Insight generated — patient={} risk={} verificationWarnings={} knowledge={}",
                patientId.value(), pred.riskCategory(),
                verificationWarnings.size(), evidence.retrievedKnowledge().size());

        // ── Step 6: Audit (REQUIRES_NEW transaction — non-fatal) ─────────────
        auditWriter.write(patientId, response);

        return response;
    }
}
