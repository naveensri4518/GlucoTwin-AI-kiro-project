package com.glucotwin.application.insight;

import com.glucotwin.domain.insight.AgentResult;
import com.glucotwin.domain.insight.ClinicalInsightResponse;
import com.glucotwin.domain.insight.ClinicalKnowledgeEvidence;
import com.glucotwin.domain.insight.ClinicalKnowledgeRetriever;
import com.glucotwin.domain.insight.EvidenceAggregationResult;
import com.glucotwin.domain.insight.ObservedSignal;
import com.glucotwin.domain.insight.PredictionAnalysisResult;
import com.glucotwin.domain.insight.TwinAnalysisResult;
import com.glucotwin.domain.prediction.ConfidenceInterval;
import com.glucotwin.domain.prediction.ContributingFactor;
import com.glucotwin.domain.prediction.RiskCategory;
import com.glucotwin.domain.prediction.RiskDirection;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.twin.TwinStatus;
import com.glucotwin.infrastructure.knowledge.LocalClinicalKnowledgeRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Phase 9 — Clinical Knowledge Retrieval tests.
 *
 * Tests 1–6: LocalClinicalKnowledgeRetriever behaviour
 * Tests 7–14: Integration with RiskEvidenceAgent and GenerateClinicalInsightUseCase
 */
@ExtendWith(MockitoExtension.class)
class Phase9KnowledgeRetrievalTest {

    // ── System under test (retriever) ─────────────────────────────────────────
    private LocalClinicalKnowledgeRetriever retriever;

    // ── Mocks for use-case tests ───────────────────────────────────────────────
    @Mock private TwinAnalysisAgent twinAgent;
    @Mock private PredictionAnalysisAgent predictionAgent;
    @Mock private ClinicalKnowledgeRetriever mockRetriever;

    private PatientId patientId;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private static final UUID PREDICTION_ID = UUID.randomUUID();

    private static TwinAnalysisResult twinResult() {
        return new TwinAnalysisResult(3, TwinStatus.ACTIVE,
                List.of(ObservedSignal.of("currentGlucose", "8.4", "mmol/L")),
                List.of(), true, true);
    }

    private static PredictionAnalysisResult predResult() {
        return new PredictionAnalysisResult(PREDICTION_ID, 0.72, RiskCategory.HIGH,
                new ConfidenceInterval(0.61, 0.83),
                List.of(new ContributingFactor("cgm_current", 0.40, RiskDirection.INCREASES_RISK),
                        new ContributingFactor("hba1c_latest", 0.20, RiskDirection.INCREASES_RISK)),
                2, "xgb-v2.0", 3, List.of());
    }

    private static ClinicalKnowledgeEvidence knowledgeItem(String id) {
        return ClinicalKnowledgeEvidence.of(id, "Test Title " + id,
                "Test Source", "Test Ref", "1.0.0", "test_topic",
                "General clinical context for test item " + id + ".");
    }

    @BeforeEach
    void setUp() {
        retriever = new LocalClinicalKnowledgeRetriever();
        patientId = PatientId.random();
    }

    // ── Test 1: Retriever returns relevant knowledge for a known topic ─────────

    @Test
    void retriever_knownTopic_returnsRelevantItems() {
        List<ClinicalKnowledgeEvidence> results =
                retriever.retrieve(List.of("cgm_current"), 5);

        assertThat(results).isNotEmpty();
        // K004 (CGM) should match cgm_current
        assertThat(results).anyMatch(e -> e.knowledgeId().equals("K004"));
    }

    // ── Test 2: Retriever returns empty result for an unknown topic ────────────

    @Test
    void retriever_unknownTopic_returnsEmptyList() {
        List<ClinicalKnowledgeEvidence> results =
                retriever.retrieve(List.of("zzz_totally_unknown_xyz"), 5);

        assertThat(results).isEmpty();
    }

    // ── Test 3: Results are deterministically ranked ───────────────────────────

    @Test
    void retriever_sameTopics_alwaysReturnsSameOrder() {
        List<String> topics = List.of("cgm_current", "hba1c_latest");

        List<ClinicalKnowledgeEvidence> first  = retriever.retrieve(topics, 5);
        List<ClinicalKnowledgeEvidence> second = retriever.retrieve(topics, 5);

        assertThat(first).isEqualTo(second);
    }

    // ── Test 4: Result count is bounded by maxResults ─────────────────────────

    @Test
    void retriever_manyMatchingTopics_resultsBoundedByMaxResults() {
        // Many broad topics that should hit multiple corpus entries
        List<String> topics = List.of("cgm", "glucose", "hba1c", "sleep", "activity",
                "bmi", "risk", "fasting", "carb", "variability");

        List<ClinicalKnowledgeEvidence> results = retriever.retrieve(topics, 3);

        assertThat(results).hasSizeLessThanOrEqualTo(3);
    }

    // ── Test 5: Source metadata is preserved ──────────────────────────────────

    @Test
    void retriever_results_preserveFullSourceMetadata() {
        List<ClinicalKnowledgeEvidence> results =
                retriever.retrieve(List.of("cgm_current"), 5);

        assertThat(results).isNotEmpty();
        results.forEach(item -> {
            assertThat(item.knowledgeId()).isNotBlank();
            assertThat(item.title()).isNotBlank();
            assertThat(item.sourceName()).isNotBlank();
            assertThat(item.sourceReference()).isNotNull();
            assertThat(item.version()).isNotBlank();
            assertThat(item.topic()).isNotBlank();
            assertThat(item.excerpt()).isNotBlank();
        });
    }

    // ── Test 6: Provenance is always CLINICAL_KNOWLEDGE ───────────────────────

    @Test
    void retriever_allResults_haveClinicialKnowledgeProvenance() {
        List<ClinicalKnowledgeEvidence> results =
                retriever.retrieve(List.of("cgm_current", "hba1c_latest", "activity", "sleep"), 10);

        assertThat(results).isNotEmpty();
        results.forEach(item ->
                assertThat(item.provenance())
                        .as("Every result must carry CLINICAL_KNOWLEDGE provenance")
                        .isEqualTo(ClinicalKnowledgeEvidence.PROVENANCE_CLINICAL_KNOWLEDGE));
    }

    // ── Test 7: RiskEvidenceAgent integrates retrieved evidence ───────────────

    @Test
    void riskEvidenceAgent_withRetriever_attachesKnowledgeToResult() {
        List<ClinicalKnowledgeEvidence> fakeKnowledge =
                List.of(knowledgeItem("K004"), knowledgeItem("K005"));
        when(mockRetriever.retrieve(any(), anyInt())).thenReturn(fakeKnowledge);

        RiskEvidenceAgent agent = new RiskEvidenceAgent(mockRetriever);
        AgentResult<EvidenceAggregationResult> result =
                agent.aggregate(twinResult(), predResult());

        assertThat(result.isSuccess()).isTrue();
        EvidenceAggregationResult evidence = result.valueOrThrow();
        assertThat(evidence.retrievedKnowledge()).hasSize(2);
        assertThat(evidence.retrievedKnowledge()).extracting("knowledgeId")
                .containsExactlyInAnyOrder("K004", "K005");
    }

    // ── Test 8: Existing OBSERVED provenance unchanged after Phase 9 ──────────

    @Test
    void riskEvidenceAgent_observedSignals_provenanceUnchanged() {
        when(mockRetriever.retrieve(any(), anyInt())).thenReturn(List.of());
        RiskEvidenceAgent agent = new RiskEvidenceAgent(mockRetriever);

        // Build twin result with OBSERVED signals
        TwinAnalysisResult twin = new TwinAnalysisResult(3, TwinStatus.ACTIVE,
                List.of(ObservedSignal.of("heartRate", "72", "bpm")),
                List.of(), true, true);

        AgentResult<EvidenceAggregationResult> result = agent.aggregate(twin, predResult());

        // The evidence summary still contains [OBSERVED] tags
        assertThat(result.valueOrThrow().evidenceSummary()).contains("[OBSERVED]");
        assertThat(result.valueOrThrow().evidenceSummary()).contains("[PREDICTED]");
    }

    // ── Test 9: Existing PREDICTED provenance unchanged after Phase 9 ─────────

    @Test
    void riskEvidenceAgent_predictedData_provenanceUnchanged() {
        when(mockRetriever.retrieve(any(), anyInt())).thenReturn(List.of());
        RiskEvidenceAgent agent = new RiskEvidenceAgent(mockRetriever);

        AgentResult<EvidenceAggregationResult> result =
                agent.aggregate(twinResult(), predResult());

        EvidenceAggregationResult evidence = result.valueOrThrow();
        // Risk category and factors come from PREDICTED source — must be present
        assertThat(evidence.riskCategory()).isEqualTo(RiskCategory.HIGH);
        assertThat(evidence.strongestFactors()).isNotEmpty();
        assertThat(evidence.strongestFactors().get(0).factorName()).isEqualTo("cgm_current");
    }

    // ── Test 10: SIMULATED data not converted into clinical knowledge ──────────

    @Test
    void clinicalKnowledgeEvidence_cannotHaveSimulatedProvenance() {
        assertThatThrownBy(() ->
                new ClinicalKnowledgeEvidence("K001", "Title", "Source", "Ref",
                        "1.0", "topic", "excerpt", "SIMULATED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLINICAL_KNOWLEDGE");
    }

    // ── Test 11: Missing knowledge does not fail insight generation ────────────

    @Test
    void riskEvidenceAgent_retrieverReturnsEmpty_insightStillSucceeds() {
        when(mockRetriever.retrieve(any(), anyInt())).thenReturn(List.of());
        RiskEvidenceAgent agent = new RiskEvidenceAgent(mockRetriever);

        AgentResult<EvidenceAggregationResult> result =
                agent.aggregate(twinResult(), predResult());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.valueOrThrow().retrievedKnowledge()).isEmpty();
    }

    // ── Test 12: Knowledge retrieval failure is non-fatal ─────────────────────

    @Test
    void riskEvidenceAgent_retrieverThrows_insightStillSucceeds() {
        when(mockRetriever.retrieve(any(), anyInt()))
                .thenThrow(new RuntimeException("Retriever internal error"));
        RiskEvidenceAgent agent = new RiskEvidenceAgent(mockRetriever);

        // Must NOT propagate the exception
        AgentResult<EvidenceAggregationResult> result =
                agent.aggregate(twinResult(), predResult());

        assertThat(result.isSuccess()).isTrue();
        // knowledge list should be empty (fallback to empty on retrieval failure)
        assertThat(result.valueOrThrow().retrievedKnowledge()).isEmpty();
    }

    // ── Test 13: Safety disclaimer remains present in final response ──────────

    @Test
    void generateInsightUseCase_safetyDisclaimer_alwaysPresent() {
        when(twinAgent.analyse(patientId)).thenReturn(AgentResult.success(twinResult()));
        when(predictionAgent.analyse(patientId)).thenReturn(AgentResult.success(predResult()));

        // Real retriever (empty result is fine)
        RiskEvidenceAgent evidenceAgent = new RiskEvidenceAgent(retriever);
        GenerateClinicalInsightUseCase useCase =
                new GenerateClinicalInsightUseCase(twinAgent, predictionAgent, evidenceAgent);

        ClinicalInsightResponse response = useCase.execute(patientId, "test question");

        assertThat(response.safetyDisclaimer())
                .isEqualTo(ClinicalInsightResponse.SAFETY_DISCLAIMER);
        assertThat(response.safetyDisclaimer())
                .contains("clinical decision support");
    }

    // ── Test 14: No patient-specific data persisted into knowledge corpus ──────

    @Test
    void retriever_topicInputsAreStructuralNamesOnly_noPatientValues() {
        // The retriever receives structural feature names ("cgm_current", "hba1c_latest")
        // not actual patient values. This test verifies those structural names do trigger
        // retrieval, and that the returned excerpts contain only general text.
        List<ClinicalKnowledgeEvidence> results =
                retriever.retrieve(List.of("cgm_current", "hba1c_latest"), 5);

        assertThat(results).isNotEmpty();
        results.forEach(item -> {
            // Excerpt must be general educational text, not contain numeric patient values
            assertThat(item.excerpt()).doesNotMatch(".*\\b\\d+\\.\\d+ mmol/L\\b.*");
            // Provenance must be CLINICAL_KNOWLEDGE — never OBSERVED or PREDICTED
            assertThat(item.provenance()).isNotEqualTo("OBSERVED");
            assertThat(item.provenance()).isNotEqualTo("PREDICTED");
            assertThat(item.provenance()).isNotEqualTo("SIMULATED");
            assertThat(item.provenance())
                    .isEqualTo(ClinicalKnowledgeEvidence.PROVENANCE_CLINICAL_KNOWLEDGE);
        });
    }
}
