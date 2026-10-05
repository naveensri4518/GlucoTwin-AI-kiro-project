package com.glucotwin.evaluation;

import com.glucotwin.application.insight.GenerateClinicalInsightUseCase;
import com.glucotwin.application.insight.InsightAuditWriter;
import com.glucotwin.application.insight.InsightExplanationAgent;
import com.glucotwin.application.insight.InsightVerificationAgent;
import com.glucotwin.application.insight.PredictionAnalysisAgent;
import com.glucotwin.application.insight.RiskEvidenceAgent;
import com.glucotwin.application.insight.TwinAnalysisAgent;
import com.glucotwin.domain.insight.AgentResult;
import com.glucotwin.domain.insight.AgentStepTrace;
import com.glucotwin.domain.insight.ClinicalInsightResponse;
import com.glucotwin.domain.insight.EvidenceAggregationResult;
import com.glucotwin.domain.insight.InsightExplanationResult;
import com.glucotwin.domain.insight.PredictionAnalysisResult;
import com.glucotwin.domain.insight.TwinAnalysisResult;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.infrastructure.knowledge.LocalClinicalKnowledgeRetriever;
import com.glucotwin.test.factory.ScenarioFixtureFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Phase 14 — Golden Clinical Insight Pipeline Scenario Tests.
 *
 * Six deterministic scenarios proving the full pipeline produces correct
 * responses for known clinical situations. Uses real verification logic
 * (InsightVerificationAgent — not mocked), real knowledge retriever, and
 * real evidence aggregation. Only the external data boundaries are mocked:
 * TwinAnalysisAgent and PredictionAnalysisAgent (which would need a DB).
 *
 * Every scenario verifies all required invariants:
 * - response is not null
 * - canonical safety disclaimer
 * - dataProvenance = "OBSERVED+PREDICTED"
 * - no SIMULATED provenance in clinical evidence
 * - expected verification warnings
 * - execution trace exists with exactly 7 stages in correct order
 */
@Tag("evaluation")
@ExtendWith(MockitoExtension.class)
class ClinicalInsightPipelineScenarioTest {

    @Mock private TwinAnalysisAgent    twinAgent;
    @Mock private PredictionAnalysisAgent predictionAgent;

    private GenerateClinicalInsightUseCase useCase;
    private PatientId patientId;

    @BeforeEach
    void setUp() {
        // Real implementations — not mocked
        RiskEvidenceAgent evidenceAgent    = new RiskEvidenceAgent(new LocalClinicalKnowledgeRetriever());
        InsightVerificationAgent verifier  = new InsightVerificationAgent();

        // No-op explanation agent — no real LLM in evaluation tests
        InsightExplanationAgent noLlm = new InsightExplanationAgent(null, null) {
            @Override
            public InsightExplanationResult explain(
                    TwinAnalysisResult t, PredictionAnalysisResult p,
                    EvidenceAggregationResult e, String q) {
                return InsightExplanationResult.unavailable();
            }
        };

        // No-op audit writer — no DB in evaluation tests
        InsightAuditWriter noAudit = new InsightAuditWriter(null, null) {
            @Override
            public void write(com.glucotwin.domain.shared.PatientId pid,
                              ClinicalInsightResponse r) { /* no-op */ }
        };

        useCase    = new GenerateClinicalInsightUseCase(
                twinAgent, predictionAgent, evidenceAgent, noLlm, verifier, noAudit);
        patientId  = PatientId.of(ScenarioFixtureFactory.FIXED_PATIENT_ID);
    }

    // ── Common invariant assertions ───────────────────────────────────────────

    private void assertCommonInvariants(ClinicalInsightResponse r) {
        assertThat(r).isNotNull();
        // Safety disclaimer always verbatim
        assertThat(r.safetyDisclaimer()).isEqualTo(ClinicalInsightResponse.SAFETY_DISCLAIMER);
        // Provenance label never changes
        assertThat(r.dataProvenance()).isEqualTo("OBSERVED+PREDICTED");
        // SIMULATED must never appear in clinical insight evidence
        assertThat(r.dataProvenance()).doesNotContain("SIMULATED");
        r.keyObservedSignals().forEach(s ->
                assertThat(s.provenance()).isNotEqualTo("SIMULATED"));
        r.clinicalKnowledgeEvidence().forEach(k ->
                assertThat(k.provenance()).isEqualTo("CLINICAL_KNOWLEDGE"));
        // Execution trace must exist with exactly 7 stages in correct order
        assertThat(r.executionTrace()).isNotNull();
        assertThat(r.executionTrace().steps()).hasSize(7);
        List<String> names = r.executionTrace().steps().stream()
                .map(AgentStepTrace::agentName).toList();
        assertThat(names).containsExactly(
                "Twin Analysis",
                "Prediction Analysis",
                "Risk Evidence",
                "LLM Explanation",
                "Verification",
                "Insight Assembly",
                "Audit");
        // All durations non-negative
        r.executionTrace().steps().forEach(step ->
                assertThat(step.durationMs()).isGreaterThanOrEqualTo(0L));
    }

    // ── Scenario 1: HIGH-risk active patient ──────────────────────────────────

    @Test
    void scenario1_highRiskActiveTwin_noVerificationWarnings() {
        TwinAnalysisResult twin = ScenarioFixtureFactory.activeTwin(3);
        PredictionAnalysisResult pred = ScenarioFixtureFactory.highRiskPred(3); // version matches

        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twin));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(pred));

        ClinicalInsightResponse r = useCase.execute(patientId, "");

        assertCommonInvariants(r);
        assertThat(r.riskCategory().name()).isEqualTo("HIGH");
        assertThat(r.spikeProbability()).isEqualTo(0.72);
        // No verification warnings: version matches, ACTIVE twin, narrow CI, has dynamic layer
        assertThat(r.dataQualityWarnings()).isEmpty();
    }

    // ── Scenario 2: LOW-risk active patient ───────────────────────────────────

    @Test
    void scenario2_lowRiskActiveTwin_verificationPasses() {
        TwinAnalysisResult twin = ScenarioFixtureFactory.activeTwin(3);
        PredictionAnalysisResult pred = ScenarioFixtureFactory.lowRiskPred();

        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twin));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(pred));

        ClinicalInsightResponse r = useCase.execute(patientId, "");

        assertCommonInvariants(r);
        assertThat(r.riskCategory().name()).isEqualTo("LOW");
        assertThat(r.spikeProbability()).isEqualTo(0.15);
        // No verification warnings
        assertThat(r.dataQualityWarnings()).isEmpty();
    }

    // ── Scenario 3: Stale Digital Twin ────────────────────────────────────────

    @Test
    void scenario3_staleTwin_verificationWarningContainsStale() {
        TwinAnalysisResult twin = ScenarioFixtureFactory.staleTwin(); // STALE
        PredictionAnalysisResult pred = ScenarioFixtureFactory.highRiskPred(3);

        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twin));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(pred));

        ClinicalInsightResponse r = useCase.execute(patientId, "");

        assertCommonInvariants(r);
        assertThat(r.dataQualityWarnings()).anyMatch(w -> w.contains("STALE"));
        assertThat(r.dataQualityWarnings()).anyMatch(w ->
                w.contains("insight confidence is reduced"));
    }

    // ── Scenario 4: Wide confidence interval ─────────────────────────────────

    @Test
    void scenario4_wideCi_verificationWarningContainsConfidenceInterval() {
        TwinAnalysisResult twin = ScenarioFixtureFactory.activeTwin(3);
        PredictionAnalysisResult pred = ScenarioFixtureFactory.wideCIPred(); // width=0.70

        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twin));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(pred));

        ClinicalInsightResponse r = useCase.execute(patientId, "");

        assertCommonInvariants(r);
        assertThat(r.dataQualityWarnings())
                .anyMatch(w -> w.contains("Confidence interval is wide"));
        assertThat(r.dataQualityWarnings())
                .anyMatch(w -> w.contains("prediction uncertainty is elevated"));
    }

    // ── Scenario 5: Missing dynamic layer ────────────────────────────────────

    @Test
    void scenario5_missingDynamicLayer_verificationWarningContainsWearable() {
        TwinAnalysisResult twin = ScenarioFixtureFactory.noDynamicLayerTwin(); // no dynamic layer
        PredictionAnalysisResult pred = ScenarioFixtureFactory.highRiskPred(3);

        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twin));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(pred));

        ClinicalInsightResponse r = useCase.execute(patientId, "");

        assertCommonInvariants(r);
        assertThat(r.dataQualityWarnings())
                .anyMatch(w -> w.contains("No wearable readings"));
        assertThat(r.dataQualityWarnings())
                .anyMatch(w -> w.contains("static data only"));
    }

    // ── Scenario 6: Twin version mismatch ────────────────────────────────────

    @Test
    void scenario6_twinVersionMismatch_verificationWarningContainsEarlierVersion() {
        TwinAnalysisResult twin  = ScenarioFixtureFactory.activeTwin(5);     // current version = 5
        PredictionAnalysisResult pred = ScenarioFixtureFactory.highRiskPred(3); // pred version = 3

        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twin));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(pred));

        ClinicalInsightResponse r = useCase.execute(patientId, "");

        assertCommonInvariants(r);
        assertThat(r.dataQualityWarnings())
                .anyMatch(w -> w.contains("earlier twin version"));
        // Pred twin v3, current twin v5 — both mentioned
        assertThat(r.dataQualityWarnings())
                .anyMatch(w -> w.contains("v3") && w.contains("v5"));
    }
}
