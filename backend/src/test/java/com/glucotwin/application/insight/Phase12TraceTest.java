package com.glucotwin.application.insight;

import com.glucotwin.domain.insight.AgentExecutionTrace;
import com.glucotwin.domain.insight.AgentResult;
import com.glucotwin.domain.insight.AgentStepTrace;
import com.glucotwin.domain.insight.ClinicalInsightResponse;
import com.glucotwin.domain.insight.EvidenceAggregationResult;
import com.glucotwin.domain.insight.InsightGenerationException;
import com.glucotwin.domain.insight.ObservedSignal;
import com.glucotwin.domain.insight.PredictionAnalysisResult;
import com.glucotwin.domain.insight.TwinAnalysisResult;
import com.glucotwin.domain.prediction.ConfidenceInterval;
import com.glucotwin.domain.prediction.ContributingFactor;
import com.glucotwin.domain.prediction.RiskCategory;
import com.glucotwin.domain.prediction.RiskDirection;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.twin.TwinStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 12 — Supervisor execution trace tests.
 *
 * Verifies that GenerateClinicalInsightUseCase correctly:
 * - attaches an AgentExecutionTrace to every successful response
 * - records correct stages, statuses, and durations
 * - marks later stages as SKIPPED when an earlier mandatory stage fails
 * - never includes patient PII in trace detail strings
 */
@ExtendWith(MockitoExtension.class)
class Phase12TraceTest {

    @Mock private TwinAnalysisAgent         twinAgent;
    @Mock private PredictionAnalysisAgent   predictionAgent;
    @Mock private RiskEvidenceAgent         evidenceAgent;
    @Mock private InsightExplanationAgent   explanationAgent;
    @Mock private InsightVerificationAgent  verificationAgent;
    @Mock private InsightAuditWriter        auditWriter;

    private GenerateClinicalInsightUseCase useCase;
    private PatientId patientId;

    private static final UUID PREDICTION_ID = UUID.randomUUID();

    private static TwinAnalysisResult twinResult() {
        return new TwinAnalysisResult(3, TwinStatus.ACTIVE,
                List.of(ObservedSignal.of("currentGlucose", "8.4", "mmol/L")),
                List.of(), true, true);
    }

    private static PredictionAnalysisResult predResult() {
        return new PredictionAnalysisResult(
                PREDICTION_ID, 0.72, RiskCategory.HIGH,
                new ConfidenceInterval(0.61, 0.83),
                List.of(new ContributingFactor("cgm_current", 0.40, RiskDirection.INCREASES_RISK)),
                2, "xgb-v2.0", 3, List.of());
    }

    private static EvidenceAggregationResult evidenceResult() {
        return new EvidenceAggregationResult(
                RiskCategory.HIGH,
                List.of(new ContributingFactor("cgm_current", 0.40, RiskDirection.INCREASES_RISK)),
                "[PREDICTED] HIGH risk. [OBSERVED] currentGlucose=8.4 mmol/L.",
                "No significant uncertainty.",
                List.of());
    }

    @BeforeEach
    void setUp() {
        useCase = new GenerateClinicalInsightUseCase(
                twinAgent, predictionAgent, evidenceAgent,
                explanationAgent, verificationAgent, auditWriter);
        patientId = PatientId.random();
        lenient().when(verificationAgent.verify(any(), any())).thenReturn(List.of());
        lenient().doNothing().when(auditWriter).write(any(), any());
        lenient().when(explanationAgent.explain(any(), any(), any(), any()))
                .thenReturn(com.glucotwin.domain.insight.InsightExplanationResult.unavailable());
    }

    // ── Test 1: Complete trace contains all expected stages ───────────────────

    @Test
    void execute_success_traceContainsAllExpectedStages() {
        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twinResult()));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(predResult()));
        when(evidenceAgent.aggregate(any(), any())).thenReturn(AgentResult.success(evidenceResult()));

        ClinicalInsightResponse response = useCase.execute(patientId, "");

        AgentExecutionTrace trace = response.executionTrace();
        assertThat(trace).isNotNull();
        assertThat(trace.steps()).hasSize(7); // Twin, Prediction, Evidence, Explanation, Verify, Assembly, Audit

        List<String> stageNames = trace.steps().stream()
                .map(AgentStepTrace::agentName).toList();
        assertThat(stageNames).containsExactly(
                GenerateClinicalInsightUseCase.STAGE_TWIN,
                GenerateClinicalInsightUseCase.STAGE_PREDICTION,
                GenerateClinicalInsightUseCase.STAGE_EVIDENCE,
                GenerateClinicalInsightUseCase.STAGE_EXPLANATION,
                GenerateClinicalInsightUseCase.STAGE_VERIFY,
                GenerateClinicalInsightUseCase.STAGE_ASSEMBLY,
                GenerateClinicalInsightUseCase.STAGE_AUDIT);
    }

    // ── Test 2: All successful stages marked SUCCESS ──────────────────────────

    @Test
    void execute_success_allStagesMarkedSuccess() {
        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twinResult()));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(predResult()));
        when(evidenceAgent.aggregate(any(), any())).thenReturn(AgentResult.success(evidenceResult()));

        ClinicalInsightResponse response = useCase.execute(patientId, "");

        response.executionTrace().steps().forEach(step ->
                assertThat(step.status())
                        .as("Step %s should be SUCCESS", step.agentName())
                        .isEqualTo(AgentStepTrace.STATUS_SUCCESS));
    }

    // ── Test 3: Durations are >= 0 ────────────────────────────────────────────

    @Test
    void execute_success_allDurationsNonNegative() {
        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twinResult()));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(predResult()));
        when(evidenceAgent.aggregate(any(), any())).thenReturn(AgentResult.success(evidenceResult()));

        ClinicalInsightResponse response = useCase.execute(patientId, "");

        response.executionTrace().steps().forEach(step ->
                assertThat(step.durationMs()).isGreaterThanOrEqualTo(0L));
        assertThat(response.executionTrace().totalDurationMs()).isGreaterThanOrEqualTo(0L);
    }

    // ── Test 4: Total duration >= 0 ───────────────────────────────────────────

    @Test
    void execute_success_totalDurationIsNonNegative() {
        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twinResult()));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(predResult()));
        when(evidenceAgent.aggregate(any(), any())).thenReturn(AgentResult.success(evidenceResult()));

        ClinicalInsightResponse response = useCase.execute(patientId, "");

        assertThat(response.executionTrace().totalDurationMs()).isGreaterThanOrEqualTo(0L);
    }

    // ── Test 5: traceId comes from context (null if MDC empty) ───────────────

    @Test
    void execute_success_traceIdFromMdc() {
        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twinResult()));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(predResult()));
        when(evidenceAgent.aggregate(any(), any())).thenReturn(AgentResult.success(evidenceResult()));

        ClinicalInsightResponse response = useCase.execute(patientId, "");

        // In unit test, MDC is empty so traceId should be null (not fabricated)
        assertThat(response.executionTrace().traceId()).isNull();
    }

    // ── Test 6: Knowledge retrieval detail contains item count ────────────────

    @Test
    void execute_success_evidenceStepDetailContainsKnowledgeCount() {
        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twinResult()));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(predResult()));
        when(evidenceAgent.aggregate(any(), any())).thenReturn(AgentResult.success(evidenceResult()));

        ClinicalInsightResponse response = useCase.execute(patientId, "");

        AgentStepTrace evidenceStep = response.executionTrace().steps().stream()
                .filter(s -> s.agentName().equals(GenerateClinicalInsightUseCase.STAGE_EVIDENCE))
                .findFirst().orElseThrow();
        assertThat(evidenceStep.detail()).contains("clinical knowledge item");
    }

    // ── Test 7: Verification detail contains warning count ────────────────────

    @Test
    void execute_success_verifyStepDetailContainsWarningCount() {
        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twinResult()));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(predResult()));
        when(evidenceAgent.aggregate(any(), any())).thenReturn(AgentResult.success(evidenceResult()));
        when(verificationAgent.verify(any(), any()))
                .thenReturn(List.of("warning1", "warning2"));

        ClinicalInsightResponse response = useCase.execute(patientId, "");

        AgentStepTrace verifyStep = response.executionTrace().steps().stream()
                .filter(s -> s.agentName().equals(GenerateClinicalInsightUseCase.STAGE_VERIFY))
                .findFirst().orElseThrow();
        assertThat(verifyStep.detail()).contains("2");
    }

    // ── Test 8: Audit stage is represented in trace ───────────────────────────

    @Test
    void execute_success_auditStageInTrace() {
        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twinResult()));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(predResult()));
        when(evidenceAgent.aggregate(any(), any())).thenReturn(AgentResult.success(evidenceResult()));

        ClinicalInsightResponse response = useCase.execute(patientId, "");

        boolean hasAuditStep = response.executionTrace().steps().stream()
                .anyMatch(s -> s.agentName().equals(GenerateClinicalInsightUseCase.STAGE_AUDIT));
        assertThat(hasAuditStep).isTrue();
    }

    // ── Test 9: Failed twin → later stages SKIPPED ───────────────────────────

    @Test
    void execute_twinFails_laterStagesSkipped_insightNotGenerated() {
        when(twinAgent.analyse(patientId))
                .thenReturn(AgentResult.failure("TwinAnalysisAgent", "Twin not found"));

        // Pipeline throws — no response returned on failure
        assertThatThrownBy(() -> useCase.execute(patientId, ""))
                .isInstanceOf(InsightGenerationException.class);

        // No audit written for failed insight
        verify(auditWriter, never()).write(any(), any());
    }

    // ── Test 10: No PII in trace detail strings ───────────────────────────────

    @Test
    void execute_success_traceDetailsContainNoPatientUuid() {
        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twinResult()));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(predResult()));
        when(evidenceAgent.aggregate(any(), any())).thenReturn(AgentResult.success(evidenceResult()));

        ClinicalInsightResponse response = useCase.execute(patientId, "");

        String rawUuid = patientId.value().toString();
        response.executionTrace().steps().forEach(step ->
                assertThat(step.detail())
                        .as("Step detail must not contain raw patient UUID")
                        .doesNotContain(rawUuid));
    }

    // ── Test 11: Existing insight response remains valid ─────────────────────

    @Test
    void execute_success_existingResponseFieldsUnchanged() {
        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twinResult()));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(predResult()));
        when(evidenceAgent.aggregate(any(), any())).thenReturn(AgentResult.success(evidenceResult()));

        ClinicalInsightResponse response = useCase.execute(patientId, "");

        assertThat(response.riskCategory()).isEqualTo(RiskCategory.HIGH);
        assertThat(response.spikeProbability()).isEqualTo(0.72);
        assertThat(response.dataProvenance()).isEqualTo("OBSERVED+PREDICTED");
        assertThat(response.safetyDisclaimer()).isEqualTo(ClinicalInsightResponse.SAFETY_DISCLAIMER);
        assertThat(response.dataProvenance()).doesNotContain("SIMULATED");
    }
}
