package com.glucotwin.application.insight;

import com.glucotwin.domain.insight.AgentExecutionTrace;
import com.glucotwin.domain.insight.AgentResult;
import com.glucotwin.domain.insight.AgentStepTrace;
import com.glucotwin.domain.insight.ClinicalInsightResponse;
import com.glucotwin.domain.insight.EvidenceAggregationResult;
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
 * Agent Supervisor — orchestrates the full Phase 12 clinical insight pipeline.
 *
 * <p>Pipeline (Phase 12):
 * <ol>
 *   <li>{@link TwinAnalysisAgent}         — reads OBSERVED twin state (read-only)
 *   <li>{@link PredictionAnalysisAgent}   — reads latest PREDICTED risk (read-only)
 *   <li>{@link RiskEvidenceAgent}         — aggregates evidence + retrieves CLINICAL_KNOWLEDGE
 *   <li>{@link InsightVerificationAgent}  — deterministic consistency checks (non-blocking)
 *   <li>Insight Assembly                  — constructs {@link ClinicalInsightResponse}
 *   <li>{@link InsightAuditWriter}        — durable audit in its own REQUIRES_NEW transaction
 * </ol>
 *
 * <p>Phase 12 additions:
 * <ul>
 *   <li>Each stage is timed via {@code System.nanoTime()} and recorded as an
 *       {@link AgentStepTrace} (SUCCESS / FAILURE / SKIPPED).
 *   <li>The full {@link AgentExecutionTrace} is attached to the response.
 *   <li>Audit timing is tracked separately: {@code totalDurationMs} covers the
 *       clinical insight pipeline up to and including insight assembly. The audit
 *       step duration is recorded in the trace but is NOT included in
 *       {@code totalDurationMs} because the audit runs in a separate REQUIRES_NEW
 *       transaction and its latency must not inflate the perceived insight latency.
 *   <li>No patient PII is stored in trace detail strings.
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
    static final String STAGE_TWIN       = "Twin Analysis";
    static final String STAGE_PREDICTION = "Prediction Analysis";
    static final String STAGE_EVIDENCE   = "Risk Evidence";
    static final String STAGE_VERIFY     = "Verification";
    static final String STAGE_ASSEMBLY   = "Insight Assembly";
    static final String STAGE_AUDIT      = "Audit";

    private final TwinAnalysisAgent         twinAgent;
    private final PredictionAnalysisAgent   predictionAgent;
    private final RiskEvidenceAgent         evidenceAgent;
    private final InsightVerificationAgent  verificationAgent;
    private final InsightAuditWriter        auditWriter;

    @Transactional(readOnly = true)
    public ClinicalInsightResponse execute(PatientId patientId, String question) {
        final String traceId      = MDC.get("traceId");
        final Instant startedAt   = Instant.now();
        final long pipelineStart  = System.nanoTime();

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
            steps.add(AgentStepTrace.skipped(STAGE_VERIFY));
            steps.add(AgentStepTrace.skipped(STAGE_ASSEMBLY));
            steps.add(AgentStepTrace.skipped(STAGE_AUDIT));
            log.warn("[AgentSupervisor] PredictionAnalysisAgent failed: {}", f.reason());
            throw InsightGenerationException.predictionUnavailable(patientId.value().toString());
        }
        PredictionAnalysisResult pred = predResult.valueOrThrow();
        steps.add(AgentStepTrace.success(STAGE_PREDICTION, predMs,
                "Latest prediction analyzed — risk=" + pred.riskCategory().name()));

        // ── Step 3: Risk Evidence (includes knowledge retrieval internally) ───
        long t2 = System.nanoTime();
        AgentResult<EvidenceAggregationResult> evidenceResult =
                evidenceAgent.aggregate(twin, pred);
        long evidenceMs = msElapsed(t2);

        if (!evidenceResult.isSuccess()) {
            AgentResult.Failure<EvidenceAggregationResult> f =
                    (AgentResult.Failure<EvidenceAggregationResult>) evidenceResult;
            steps.add(AgentStepTrace.failure(STAGE_EVIDENCE, evidenceMs, f.reason()));
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

        // ── Step 4: Verification ──────────────────────────────────────────────
        long t3 = System.nanoTime();
        List<String> verificationWarnings = verificationAgent.verify(twin, pred);
        long verifyMs = msElapsed(t3);
        steps.add(AgentStepTrace.success(STAGE_VERIFY, verifyMs,
                "Verification completed with " + verificationWarnings.size() + " warning(s)"));

        // ── Step 5: Insight Assembly ──────────────────────────────────────────
        List<String> allWarnings = new ArrayList<>(evidence.combinedDataQualityWarnings());
        allWarnings.addAll(verificationWarnings);

        // totalDurationMs covers steps 1–5 (clinical pipeline only, before audit)
        long clinicalPipelineMs = msElapsed(pipelineStart);

        long t4 = System.nanoTime();
        AgentExecutionTrace partialTrace = new AgentExecutionTrace(
                traceId, startedAt, clinicalPipelineMs, steps);

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
                null); // trace attached after audit step below

        long assemblyMs = msElapsed(t4);
        steps.add(AgentStepTrace.success(STAGE_ASSEMBLY, assemblyMs,
                "Clinical insight response constructed"));

        log.info("[AgentSupervisor] Insight generated — patient={} risk={} pipelineMs={} verificationWarnings={} knowledge={}",
                patientId.value(), pred.riskCategory(), clinicalPipelineMs,
                verificationWarnings.size(), knowledgeCount);

        // ── Step 6: Audit (REQUIRES_NEW transaction — non-fatal, timed separately) ──
        long t5 = System.nanoTime();
        auditWriter.write(patientId, response);
        long auditMs = msElapsed(t5);
        steps.add(AgentStepTrace.success(STAGE_AUDIT, auditMs, "Audit record persisted"));

        // ── Assemble final trace and rebuild response with it ─────────────────
        // totalDurationMs = clinical pipeline only (excludes audit latency).
        // Audit duration IS visible in the trace steps for full observability,
        // but does not inflate the headline totalDurationMs seen by clinicians.
        AgentExecutionTrace finalTrace = new AgentExecutionTrace(
                traceId, startedAt, clinicalPipelineMs, steps);

        // Rebuild response with the complete trace attached
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
                finalTrace);
    }

    /** Convert nanoTime delta to milliseconds, safely clamped to >= 0. */
    private static long msElapsed(long startNano) {
        long delta = System.nanoTime() - startNano;
        return Math.max(0L, delta / 1_000_000L);
    }
}
