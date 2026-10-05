package com.glucotwin.application.insight;

import com.glucotwin.domain.insight.AgentExecutionTrace;
import com.glucotwin.domain.insight.AgentResult;
import com.glucotwin.domain.insight.AgentStepTrace;
import com.glucotwin.domain.insight.ClinicalInsightResponse;
import com.glucotwin.domain.insight.EvidenceAggregationResult;
import com.glucotwin.domain.insight.InsightExplanationResult;
import com.glucotwin.domain.insight.InsightGenerationException;
import com.glucotwin.domain.insight.PredictionAnalysisResult;
import com.glucotwin.domain.insight.TwinAnalysisResult;
import com.glucotwin.domain.shared.PatientId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent Supervisor — orchestrates the full Phase 13 clinical insight pipeline.
 *
 * <p>Pipeline (Phase 13):
 * <ol>
 *   <li>{@link TwinAnalysisAgent}          — reads OBSERVED twin state (read-only)
 *   <li>{@link PredictionAnalysisAgent}    — reads latest PREDICTED risk (read-only)
 *   <li>{@link RiskEvidenceAgent}          — aggregates evidence + retrieves CLINICAL_KNOWLEDGE
 *   <li>{@link InsightExplanationAgent}    — grounded LLM explanation (NON-FATAL, nullable)
 *   <li>{@link InsightVerificationAgent}   — deterministic consistency checks (non-blocking)
 *   <li>Insight Assembly                   — constructs {@link ClinicalInsightResponse}
 *   <li>{@link InsightAuditWriter}         — durable audit in its own REQUIRES_NEW transaction
 * </ol>
 *
 * <p>Phase 13 — LLM explanation rules:
 * <ul>
 *   <li>The LLM is called AFTER evidence aggregation and BEFORE verification.
 *   <li>The LLM receives only the provenance-labelled {@code evidenceSummary} string —
 *       not raw patient data.
 *   <li>LLM failure/timeout is always non-fatal — pipeline continues with {@code explanation=null}.
 *   <li>The LLM never affects {@code spikeProbability}, {@code riskCategory},
 *       {@code confidenceInterval}, or any provenance label.
 *   <li>All numeric clinical values remain sourced exclusively from the XGBoost pipeline.
 * </ul>
 *
 * <p>Transaction strategy:
 * <ul>
 *   <li>This method runs {@code @Transactional(readOnly=true)}.
 *   <li>{@link InsightAuditWriter#write} runs in a separate {@code REQUIRES_NEW} transaction.
 *   <li>Audit failure is non-fatal — the insight response is always returned.
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GenerateClinicalInsightUseCase {

    // Stage name constants used in AgentStepTrace — must match frontend display labels
    static final String STAGE_TWIN        = "Twin Analysis";
    static final String STAGE_PREDICTION  = "Prediction Analysis";
    static final String STAGE_EVIDENCE    = "Risk Evidence";
    static final String STAGE_EXPLANATION = "LLM Explanation";
    static final String STAGE_VERIFY      = "Verification";
    static final String STAGE_ASSEMBLY    = "Insight Assembly";
    static final String STAGE_AUDIT       = "Audit";

    private final TwinAnalysisAgent         twinAgent;
    private final PredictionAnalysisAgent   predictionAgent;
    private final RiskEvidenceAgent         evidenceAgent;
    private final InsightExplanationAgent   explanationAgent;
    private final InsightVerificationAgent  verificationAgent;
    private final InsightAuditWriter        auditWriter;

    @Transactional(readOnly = true)
    public ClinicalInsightResponse execute(PatientId patientId, String question) {
        final String traceId     = MDC.get("traceId");
        final Instant startedAt  = Instant.now();
        final long pipelineStart = System.nanoTime();

        log.info("[AgentSupervisor] Starting clinical insight for patient={} traceId='{}'",
                patientId.value(), traceId);

        List<AgentStepTrace> steps = new ArrayList<>();

        // ── Step 1: Twin Analysis ─────────────────────────────────────────────
        long t0 = System.nanoTime();
        AgentResult<TwinAnalysisResult> twinResult = twinAgent.analyse(patientId);
        long twinMs = msElapsed(t0);

        if (!twinResult.isSuccess()) {
            AgentResult.Failure<TwinAnalysisResult> f = (AgentResult.Failure<TwinAnalysisResult>) twinResult;
            steps.add(AgentStepTrace.failure(STAGE_TWIN, twinMs, f.reason()));
            steps.add(AgentStepTrace.skipped(STAGE_PREDICTION));
            steps.add(AgentStepTrace.skipped(STAGE_EVIDENCE));
            steps.add(AgentStepTrace.skipped(STAGE_EXPLANATION));
            steps.add(AgentStepTrace.skipped(STAGE_VERIFY));
            steps.add(AgentStepTrace.skipped(STAGE_ASSEMBLY));
            steps.add(AgentStepTrace.skipped(STAGE_AUDIT));
            log.warn("[AgentSupervisor] TwinAnalysisAgent failed: {}", f.reason());
            throw InsightGenerationException.twinUnavailable(patientId.value().toString());
        }
        TwinAnalysisResult twin = twinResult.valueOrThrow();
        steps.add(AgentStepTrace.success(STAGE_TWIN, twinMs,
                "Observed twin state analyzed — " + twin.keyObservedSignals().size() + " signal(s)"));

        // ── Step 2: Prediction Analysis ───────────────────────────────────────
        long t1 = System.nanoTime();
        AgentResult<PredictionAnalysisResult> predResult = predictionAgent.analyse(patientId);
        long predMs = msElapsed(t1);

        if (!predResult.isSuccess()) {
            AgentResult.Failure<PredictionAnalysisResult> f =
                    (AgentResult.Failure<PredictionAnalysisResult>) predResult;
            steps.add(AgentStepTrace.failure(STAGE_PREDICTION, predMs, f.reason()));
            steps.add(AgentStepTrace.skipped(STAGE_EVIDENCE));
            steps.add(AgentStepTrace.skipped(STAGE_EXPLANATION));
            steps.add(AgentStepTrace.skipped(STAGE_VERIFY));
            steps.add(AgentStepTrace.skipped(STAGE_ASSEMBLY));
            steps.add(AgentStepTrace.skipped(STAGE_AUDIT));
            log.warn("[AgentSupervisor] PredictionAnalysisAgent failed: {}", f.reason());
            throw InsightGenerationException.predictionUnavailable(patientId.value().toString());
        }
        PredictionAnalysisResult pred = predResult.valueOrThrow();
        steps.add(AgentStepTrace.success(STAGE_PREDICTION, predMs,
                "Latest prediction analyzed — risk=" + pred.riskCategory().name()));

        // ── Step 3: Risk Evidence (includes knowledge retrieval) ──────────────
        long t2 = System.nanoTime();
        AgentResult<EvidenceAggregationResult> evidenceResult =
                evidenceAgent.aggregate(twin, pred);
        long evidenceMs = msElapsed(t2);

        if (!evidenceResult.isSuccess()) {
            AgentResult.Failure<EvidenceAggregationResult> f =
                    (AgentResult.Failure<EvidenceAggregationResult>) evidenceResult;
            steps.add(AgentStepTrace.failure(STAGE_EVIDENCE, evidenceMs, f.reason()));
            steps.add(AgentStepTrace.skipped(STAGE_EXPLANATION));
            steps.add(AgentStepTrace.skipped(STAGE_VERIFY));
            steps.add(AgentStepTrace.skipped(STAGE_ASSEMBLY));
            steps.add(AgentStepTrace.skipped(STAGE_AUDIT));
            log.warn("[AgentSupervisor] RiskEvidenceAgent failed: {}", f.reason());
            throw InsightGenerationException.agentFailure("RiskEvidenceAgent", f.reason());
        }
        EvidenceAggregationResult evidence = evidenceResult.valueOrThrow();
        int knowledgeCount = evidence.retrievedKnowledge().size();
        steps.add(AgentStepTrace.success(STAGE_EVIDENCE, evidenceMs,
                "Evidence aggregation completed — retrieved " + knowledgeCount
                + " clinical knowledge item(s)"));

        // ── Step 4: LLM Explanation (NON-FATAL — never blocks pipeline) ───────
        long t3 = System.nanoTime();
        InsightExplanationResult explanationResult =
                explanationAgent.explain(twin, pred, evidence, question);
        long explanationMs = msElapsed(t3);

        String explanationText = null;
        if (explanationResult.hasContent()) {
            explanationText = explanationResult.explanation();
            steps.add(AgentStepTrace.success(STAGE_EXPLANATION, explanationMs,
                    "Explanation generated — model=" + explanationResult.modelId()
                    + " tokens=" + (explanationResult.promptTokens()
                                  + explanationResult.completionTokens())));
        } else {
            steps.add(AgentStepTrace.success(STAGE_EXPLANATION, explanationMs,
                    "Explanation unavailable (disabled, timed out, or LLM error)"));
        }

        // ── Step 5: Verification ──────────────────────────────────────────────
        long t4 = System.nanoTime();
        List<String> verificationWarnings = verificationAgent.verify(twin, pred);
        long verifyMs = msElapsed(t4);
        steps.add(AgentStepTrace.success(STAGE_VERIFY, verifyMs,
                "Verification completed with " + verificationWarnings.size() + " warning(s)"));

        // ── Step 6: Insight Assembly ──────────────────────────────────────────
        List<String> allWarnings = new ArrayList<>(evidence.combinedDataQualityWarnings());
        allWarnings.addAll(verificationWarnings);

        // totalDurationMs covers the clinical pipeline (steps 1–6, before audit)
        long clinicalPipelineMs = msElapsed(pipelineStart);

        long t5 = System.nanoTime();
        ClinicalInsightResponse response = new ClinicalInsightResponse(
                patientId.value(),
                startedAt,
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
                evidence.retrievedKnowledge(),
                null,          // trace attached after audit step below
                explanationText);

        long assemblyMs = msElapsed(t5);
        steps.add(AgentStepTrace.success(STAGE_ASSEMBLY, assemblyMs,
                "Clinical insight response constructed"));

        log.info("[AgentSupervisor] Insight generated — patient={} risk={} pipelineMs={} "
                + "explanation={} verificationWarnings={} knowledge={}",
                patientId.value(), pred.riskCategory(), clinicalPipelineMs,
                explanationResult.hasContent(), verificationWarnings.size(), knowledgeCount);

        // ── Step 7: Audit (REQUIRES_NEW transaction — non-fatal) ─────────────
        long t6 = System.nanoTime();
        auditWriter.write(patientId, response);
        long auditMs = msElapsed(t6);
        steps.add(AgentStepTrace.success(STAGE_AUDIT, auditMs, "Audit record persisted"));

        // ── Assemble final trace and rebuild response ─────────────────────────
        AgentExecutionTrace finalTrace = new AgentExecutionTrace(
                traceId, startedAt, clinicalPipelineMs, steps);

        return new ClinicalInsightResponse(
                response.patientId(),
                response.generatedAt(),
                response.twinStateVersion(),
                response.latestPredictionId(),
                response.riskCategory(),
                response.spikeProbability(),
                response.confidenceInterval(),
                response.keyObservedSignals(),
                response.contributingFactors(),
                response.dataQualityWarnings(),
                response.evidenceSummary(),
                response.uncertainty(),
                response.dataProvenance(),
                response.safetyDisclaimer(),
                response.clinicalKnowledgeEvidence(),
                finalTrace,
                response.explanation());
    }

    private static long msElapsed(long startNano) {
        return Math.max(0L, (System.nanoTime() - startNano) / 1_000_000L);
    }
}
