package com.glucotwin.test.factory;

import com.glucotwin.domain.insight.AgentExecutionTrace;
import com.glucotwin.domain.insight.ClinicalInsightResponse;
import com.glucotwin.domain.insight.ClinicalKnowledgeEvidence;
import com.glucotwin.domain.insight.ObservedSignal;
import com.glucotwin.domain.insight.PredictionAnalysisResult;
import com.glucotwin.domain.insight.TwinAnalysisResult;
import com.glucotwin.domain.prediction.ConfidenceInterval;
import com.glucotwin.domain.prediction.ContributingFactor;
import com.glucotwin.domain.prediction.RiskCategory;
import com.glucotwin.domain.prediction.RiskDirection;
import com.glucotwin.domain.simulation.SimulationResult;
import com.glucotwin.domain.simulation.SimulationScenario;
import com.glucotwin.domain.shared.DataProvenance;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.twin.ActivityLevel;
import com.glucotwin.domain.twin.TwinStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Shared deterministic test fixtures for Phase 14 evaluation scenarios.
 *
 * <p>All values are obviously synthetic/test-only. No production logic here.
 * Nullable Phase 12/13 fields (executionTrace, explanation) default to null.
 */
public final class ScenarioFixtureFactory {

    private ScenarioFixtureFactory() {}

    public static final UUID FIXED_PATIENT_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID FIXED_PREDICTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    // ── Twin Analysis Results ──────────────────────────────────────────────────

    /** Active twin, version 3, both layers present, narrow CI match. */
    public static TwinAnalysisResult activeTwin(int version) {
        return new TwinAnalysisResult(version, TwinStatus.ACTIVE,
                List.of(ObservedSignal.of("currentGlucose", "8.4", "mmol/L"),
                        ObservedSignal.of("heartRate", "72", "bpm")),
                List.of(), true, true);
    }

    /** Stale twin, version 3. */
    public static TwinAnalysisResult staleTwin() {
        return new TwinAnalysisResult(3, TwinStatus.STALE,
                List.of(ObservedSignal.of("currentGlucose", "9.1", "mmol/L")),
                List.of(), true, true);
    }

    /** Twin with no dynamic layer (hasDynamicLayer = false). */
    public static TwinAnalysisResult noDynamicLayerTwin() {
        return new TwinAnalysisResult(3, TwinStatus.INITIALISED,
                List.of(), List.of(), false, true);
    }

    // ── Prediction Analysis Results ───────────────────────────────────────────

    /** HIGH risk, narrow CI (width 0.22), twinStateVersion = 3. */
    public static PredictionAnalysisResult highRiskPred(int twinVersion) {
        return new PredictionAnalysisResult(
                FIXED_PREDICTION_ID, 0.72, RiskCategory.HIGH,
                new ConfidenceInterval(0.61, 0.83),
                List.of(new ContributingFactor("cgm_current", 0.40, RiskDirection.INCREASES_RISK)),
                2, "xgb-v2.0-test", twinVersion, List.of());
    }

    /** LOW risk, narrow CI, twinStateVersion = 3. */
    public static PredictionAnalysisResult lowRiskPred() {
        return new PredictionAnalysisResult(
                FIXED_PREDICTION_ID, 0.15, RiskCategory.LOW,
                new ConfidenceInterval(0.05, 0.25),
                List.of(new ContributingFactor("hba1c_latest", 0.10, RiskDirection.DECREASES_RISK)),
                2, "xgb-v2.0-test", 3, List.of());
    }

    /** HIGH risk with wide CI (width = 0.70 > 0.35 threshold). */
    public static PredictionAnalysisResult wideCIPred() {
        return new PredictionAnalysisResult(
                FIXED_PREDICTION_ID, 0.55, RiskCategory.MODERATE,
                new ConfidenceInterval(0.10, 0.80),
                List.of(), 2, "xgb-v2.0-test", 3, List.of());
    }

    // ── ClinicalInsightResponse helper ────────────────────────────────────────

    /**
     * Build a minimal but valid ClinicalInsightResponse for a given scenario.
     * executionTrace and explanation default to null (Phase 12/13 optional fields).
     */
    public static ClinicalInsightResponse minimalInsightResponse(
            RiskCategory riskCategory,
            double spikeProbability,
            ConfidenceInterval ci,
            List<String> dataQualityWarnings,
            AgentExecutionTrace trace,
            String explanation) {
        return new ClinicalInsightResponse(
                FIXED_PATIENT_ID,
                Instant.parse("2026-10-04T12:00:00Z"),
                3,
                FIXED_PREDICTION_ID,
                riskCategory,
                spikeProbability,
                ci,
                List.of(ObservedSignal.of("currentGlucose", "8.4", "mmol/L")),
                List.of(new ContributingFactor("cgm_current", 0.40, RiskDirection.INCREASES_RISK)),
                dataQualityWarnings,
                "[PREDICTED] " + riskCategory.name() + " risk. [OBSERVED] signal present.",
                "No significant uncertainty.",
                ClinicalInsightResponse.PROVENANCE_LABEL,
                ClinicalInsightResponse.SAFETY_DISCLAIMER,
                List.of(),   // no knowledge evidence in minimal fixture
                trace,
                explanation);
    }

    // ── SimulationResult helper ───────────────────────────────────────────────

    /** Build a valid SimulationResult — always SIMULATED provenance. */
    public static SimulationResult simulationResult() {
        return new SimulationResult(
                UUID.randomUUID(),
                PatientId.of(FIXED_PATIENT_ID),
                3,
                new SimulationScenario(90.0, ActivityLevel.VIGOROUS, false),
                0.65,
                RiskCategory.HIGH,
                new ConfidenceInterval(0.55, 0.75),
                List.of(),
                2,
                "xgb-v2.0-test",
                List.of(),
                0.05,
                Instant.now(),
                DataProvenance.SIMULATED);
    }

    // ── Knowledge Evidence helper ─────────────────────────────────────────────

    /** Minimal valid CLINICAL_KNOWLEDGE evidence item. */
    public static ClinicalKnowledgeEvidence knowledgeEvidence(String id) {
        return ClinicalKnowledgeEvidence.of(id, "Test Knowledge " + id,
                "Test Source", "Test Ref", "1.0.0", "test_topic",
                "General clinical context for evaluation fixture " + id + ".");
    }
}
