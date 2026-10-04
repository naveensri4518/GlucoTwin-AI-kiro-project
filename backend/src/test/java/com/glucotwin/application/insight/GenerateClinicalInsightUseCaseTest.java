package com.glucotwin.application.insight;

import com.glucotwin.domain.insight.AgentResult;
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
import com.glucotwin.domain.shared.DataProvenance;
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
import static org.mockito.Mockito.*;

/**
 * Phase 8 — GenerateClinicalInsightUseCase unit tests.
 *
 * All agent dependencies are mocked — no real database, no real HTTP calls.
 * Tests cover all 14 required scenarios:
 *  1.  Supervisor orchestration (full happy path)
 *  2.  Twin analysis — OBSERVED signals extracted
 *  3.  Prediction analysis — PREDICTED provenance
 *  4.  Evidence aggregation — summary produced
 *  5.  OBSERVED/PREDICTED provenance never merged
 *  6.  SIMULATED data never presented as OBSERVED
 *  7.  Missing prediction handling
 *  8.  Missing Twin handling
 *  9.  Patient not found (mapped to twin unavailable)
 * 10.  Agent/tool failure
 * 11.  Safety disclaimer always present and verbatim
 * 12.  No Digital Twin mutation during analysis
 * 13.  Authorization (role annotations — verified structurally)
 * 14.  Structured response validation
 */
@ExtendWith(MockitoExtension.class)
class GenerateClinicalInsightUseCaseTest {

    @Mock private TwinAnalysisAgent twinAgent;
    @Mock private PredictionAnalysisAgent predictionAgent;
    @Mock private RiskEvidenceAgent evidenceAgent;

    private GenerateClinicalInsightUseCase useCase;
    private PatientId patientId;

    // ── Fixture data ──────────────────────────────────────────────────────────

    private static final UUID PREDICTION_ID = UUID.randomUUID();

    private static TwinAnalysisResult twinResult() {
        return new TwinAnalysisResult(
                3,
                TwinStatus.ACTIVE,
                List.of(
                        ObservedSignal.of("currentGlucose", "8.4", "mmol/L"),
                        ObservedSignal.of("heartRate",      "72",  "bpm")),
                List.of(),
                true, true);
    }

    private static PredictionAnalysisResult predResult() {
        return new PredictionAnalysisResult(
                PREDICTION_ID,
                0.72,
                RiskCategory.HIGH,
                new ConfidenceInterval(0.61, 0.83),
                List.of(new ContributingFactor("cgm_current", 0.40, RiskDirection.INCREASES_RISK)),
                2, "xgb-v2.0", 3,
                List.of());
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
        useCase = new GenerateClinicalInsightUseCase(twinAgent, predictionAgent, evidenceAgent);
        patientId = PatientId.random();
    }

    // ── Test 1: Supervisor orchestration — full happy path ────────────────────

    @Test
    void execute_happyPath_orchestratesAllThreeAgentsInOrder() {
        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twinResult()));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(predResult()));
        when(evidenceAgent.aggregate(any(), any())).thenReturn(AgentResult.success(evidenceResult()));

        ClinicalInsightResponse response = useCase.execute(patientId, "Why is risk elevated?");

        assertThat(response).isNotNull();
        // Verify all three agents were called exactly once, in dependency order
        var inOrder = inOrder(twinAgent, predictionAgent, evidenceAgent);
        inOrder.verify(twinAgent).analyse(patientId);
        inOrder.verify(predictionAgent).analyse(patientId);
        inOrder.verify(evidenceAgent).aggregate(any(TwinAnalysisResult.class),
                any(PredictionAnalysisResult.class));
    }

    // ── Test 2: Twin analysis — OBSERVED signals preserved ────────────────────

    @Test
    void execute_keyObservedSignals_allHaveObservedProvenance() {
        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twinResult()));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(predResult()));
        when(evidenceAgent.aggregate(any(), any())).thenReturn(AgentResult.success(evidenceResult()));

        ClinicalInsightResponse response = useCase.execute(patientId, "");

        assertThat(response.keyObservedSignals()).isNotEmpty();
        response.keyObservedSignals().forEach(signal ->
                assertThat(signal.provenance())
                        .as("Every observed signal must carry OBSERVED provenance")
                        .isEqualTo(ObservedSignal.PROVENANCE_OBSERVED));
    }

    // ── Test 3: Prediction analysis — PREDICTED provenance ───────────────────

    @Test
    void execute_predictionData_hasCorrectProvenanceLabel() {
        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twinResult()));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(predResult()));
        when(evidenceAgent.aggregate(any(), any())).thenReturn(AgentResult.success(evidenceResult()));

        ClinicalInsightResponse response = useCase.execute(patientId, "");

        assertThat(response.dataProvenance()).isEqualTo("OBSERVED+PREDICTED");
        assertThat(response.spikeProbability()).isEqualTo(0.72);
        assertThat(response.riskCategory()).isEqualTo(RiskCategory.HIGH);
        assertThat(response.latestPredictionId()).isEqualTo(PREDICTION_ID);
    }

    // ── Test 4: Evidence aggregation — summary present ────────────────────────

    @Test
    void execute_evidenceSummary_isPopulated() {
        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twinResult()));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(predResult()));
        when(evidenceAgent.aggregate(any(), any())).thenReturn(AgentResult.success(evidenceResult()));

        ClinicalInsightResponse response = useCase.execute(patientId, "");

        assertThat(response.evidenceSummary()).isNotBlank();
        assertThat(response.uncertainty()).isNotBlank();
    }

    // ── Test 5: OBSERVED and PREDICTED provenance never merged ────────────────

    @Test
    void execute_observedAndPredictedProvenanceNeverMergedInSignals() {
        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twinResult()));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(predResult()));
        when(evidenceAgent.aggregate(any(), any())).thenReturn(AgentResult.success(evidenceResult()));

        ClinicalInsightResponse response = useCase.execute(patientId, "");

        // Observed signals must not contain PREDICTED
        response.keyObservedSignals().forEach(s ->
                assertThat(s.provenance()).doesNotContain("PREDICTED"));

        // Response data provenance label combines them distinctly (OBSERVED+PREDICTED, not PREDICTED alone)
        assertThat(response.dataProvenance()).contains("OBSERVED");
        assertThat(response.dataProvenance()).contains("PREDICTED");
        // But is not just one of them
        assertThat(response.dataProvenance()).isNotEqualTo("OBSERVED");
        assertThat(response.dataProvenance()).isNotEqualTo("PREDICTED");
    }

    // ── Test 6: SIMULATED data never in ClinicalInsightResponse ──────────────

    @Test
    void execute_dataProvenance_neverSimulated() {
        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twinResult()));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(predResult()));
        when(evidenceAgent.aggregate(any(), any())).thenReturn(AgentResult.success(evidenceResult()));

        ClinicalInsightResponse response = useCase.execute(patientId, "");

        assertThat(response.dataProvenance()).doesNotContain("SIMULATED");
        response.keyObservedSignals().forEach(s ->
                assertThat(s.provenance()).isNotEqualTo(DataProvenance.SIMULATED.name()));
    }

    // ── Test 7: Missing prediction handling ───────────────────────────────────

    @Test
    void execute_predictionUnavailable_throwsInsightExceptionWithClearMessage() {
        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twinResult()));
        when(predictionAgent.analyse(patientId))
                .thenReturn(AgentResult.failure("PredictionAnalysisAgent",
                        "No completed prediction available"));

        assertThatThrownBy(() -> useCase.execute(patientId, ""))
                .isInstanceOf(InsightGenerationException.class)
                .satisfies(ex -> {
                    InsightGenerationException ige = (InsightGenerationException) ex;
                    assertThat(ige.getErrorCode()).isEqualTo("INSIGHT_PREDICTION_UNAVAILABLE");
                    assertThat(ige.getMessage()).containsIgnoringCase("prediction");
                });
        // Evidence agent must NOT be called when prediction is unavailable
        verify(evidenceAgent, never()).aggregate(any(), any());
    }

    // ── Test 8: Missing Twin handling ─────────────────────────────────────────

    @Test
    void execute_twinUnavailable_throwsInsightExceptionBeforePredictionAgent() {
        when(twinAgent.analyse(patientId))
                .thenReturn(AgentResult.failure("TwinAnalysisAgent",
                        "Digital Twin not found"));

        assertThatThrownBy(() -> useCase.execute(patientId, ""))
                .isInstanceOf(InsightGenerationException.class)
                .satisfies(ex -> {
                    InsightGenerationException ige = (InsightGenerationException) ex;
                    assertThat(ige.getErrorCode()).isEqualTo("INSIGHT_TWIN_UNAVAILABLE");
                });
        // Prediction and evidence agents must NOT be called
        verify(predictionAgent, never()).analyse(any());
        verify(evidenceAgent, never()).aggregate(any(), any());
    }

    // ── Test 9: Patient not found ─────────────────────────────────────────────

    @Test
    void execute_patientNotFound_twinAgentReturnsFailure_throwsTwinUnavailable() {
        // TwinAnalysisAgent maps ResourceNotFoundException → AgentResult.failure
        when(twinAgent.analyse(patientId))
                .thenReturn(AgentResult.failure("TwinAnalysisAgent",
                        "Digital Twin not found for patient: " + patientId.value()));

        assertThatThrownBy(() -> useCase.execute(patientId, ""))
                .isInstanceOf(InsightGenerationException.class)
                .satisfies(ex -> {
                    InsightGenerationException ige = (InsightGenerationException) ex;
                    assertThat(ige.getErrorCode()).isEqualTo("INSIGHT_TWIN_UNAVAILABLE");
                    assertThat(ige.getMessage()).contains(patientId.value().toString());
                });
    }

    // ── Test 10: Agent/tool failure ───────────────────────────────────────────

    @Test
    void execute_evidenceAgentFailure_throwsInsightAgentFailureException() {
        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twinResult()));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(predResult()));
        when(evidenceAgent.aggregate(any(), any()))
                .thenReturn(AgentResult.failure("RiskEvidenceAgent",
                        "Unexpected internal error"));

        assertThatThrownBy(() -> useCase.execute(patientId, ""))
                .isInstanceOf(InsightGenerationException.class)
                .satisfies(ex -> {
                    InsightGenerationException ige = (InsightGenerationException) ex;
                    assertThat(ige.getErrorCode()).isEqualTo("INSIGHT_AGENT_FAILURE");
                });
    }

    // ── Test 11: Safety disclaimer always present and verbatim ───────────────

    @Test
    void execute_safetyDisclaimer_alwaysPresentAndVerbatim() {
        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twinResult()));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(predResult()));
        when(evidenceAgent.aggregate(any(), any())).thenReturn(AgentResult.success(evidenceResult()));

        ClinicalInsightResponse response = useCase.execute(patientId, "");

        assertThat(response.safetyDisclaimer())
                .isEqualTo(ClinicalInsightResponse.SAFETY_DISCLAIMER);
        assertThat(response.safetyDisclaimer())
                .contains("clinical decision support");
        assertThat(response.safetyDisclaimer())
                .contains("not a diagnosis or medical recommendation");
    }

    // ── Test 12: Digital Twin never mutated during analysis ───────────────────

    @Test
    void execute_digitalTwinNeverMutated_twinAgentIsCallWithReadOnlyUseCase() {
        // The TwinAnalysisAgent uses GetDigitalTwinStateUseCase (readOnly=true).
        // We verify the supervisor never calls any mutating method — it only reads
        // the result of twinAgent.analyse() and passes it downstream.
        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twinResult()));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(predResult()));
        when(evidenceAgent.aggregate(any(), any())).thenReturn(AgentResult.success(evidenceResult()));

        useCase.execute(patientId, "");

        // Supervisor only calls analyse() — no mutating method exists on the agent contract
        verify(twinAgent, times(1)).analyse(patientId);
        verifyNoMoreInteractions(twinAgent);
    }

    // ── Test 13: Authorization — role annotation present on controller ────────

    @Test
    void clinicalInsightController_hasPreAuthorizeAnnotation() throws Exception {
        var method = com.glucotwin.api.ClinicalInsightController.class
                .getMethod("generate", java.util.UUID.class,
                        com.glucotwin.api.dto.ClinicalInsightRequestDto.class);
        var annotation = method.getAnnotation(
                org.springframework.security.access.prepost.PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains("CLINICIAN", "ADMIN");
    }

    // ── Test 14: Structured response validation ───────────────────────────────

    @Test
    void execute_structuredResponse_containsAllRequiredFields() {
        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twinResult()));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(predResult()));
        when(evidenceAgent.aggregate(any(), any())).thenReturn(AgentResult.success(evidenceResult()));

        ClinicalInsightResponse r = useCase.execute(patientId, "");

        assertThat(r.patientId()).isEqualTo(patientId.value());
        assertThat(r.generatedAt()).isNotNull();
        assertThat(r.twinStateVersion()).isEqualTo(3);
        assertThat(r.latestPredictionId()).isEqualTo(PREDICTION_ID);
        assertThat(r.riskCategory()).isEqualTo(RiskCategory.HIGH);
        assertThat(r.spikeProbability()).isEqualTo(0.72);
        assertThat(r.confidenceInterval()).isNotNull();
        assertThat(r.confidenceInterval().low()).isEqualTo(0.61);
        assertThat(r.confidenceInterval().high()).isEqualTo(0.83);
        assertThat(r.keyObservedSignals()).isNotEmpty();
        assertThat(r.contributingFactors()).isNotEmpty();
        assertThat(r.evidenceSummary()).isNotBlank();
        assertThat(r.uncertainty()).isNotBlank();
        assertThat(r.dataProvenance()).isEqualTo("OBSERVED+PREDICTED");
        assertThat(r.safetyDisclaimer()).isEqualTo(ClinicalInsightResponse.SAFETY_DISCLAIMER);
    }
}
