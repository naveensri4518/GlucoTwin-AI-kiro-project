package com.glucotwin.application.insight;

import com.glucotwin.domain.insight.ObservedSignal;
import com.glucotwin.domain.insight.PredictionAnalysisResult;
import com.glucotwin.domain.insight.TwinAnalysisResult;
import com.glucotwin.domain.prediction.ConfidenceInterval;
import com.glucotwin.domain.prediction.ContributingFactor;
import com.glucotwin.domain.prediction.RiskCategory;
import com.glucotwin.domain.prediction.RiskDirection;
import com.glucotwin.domain.twin.TwinStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Phase 11 — InsightVerificationAgent unit tests.
 *
 * Covers all 9 required verification scenarios.
 * No mocks needed — the agent is a pure function of its two inputs.
 */
class InsightVerificationAgentTest {

    private InsightVerificationAgent agent;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    /** Active twin, twinStateVersion=3, hasDynamicLayer=true */
    private static TwinAnalysisResult activeTwin(int version) {
        return new TwinAnalysisResult(
                version, TwinStatus.ACTIVE,
                List.of(ObservedSignal.of("currentGlucose", "8.4", "mmol/L")),
                List.of(), true, true);
    }

    /** STALE twin */
    private static TwinAnalysisResult staleTwin() {
        return new TwinAnalysisResult(
                3, TwinStatus.STALE,
                List.of(ObservedSignal.of("currentGlucose", "8.4", "mmol/L")),
                List.of(), true, true);
    }

    /** Twin with no dynamic layer (hasDynamicLayer=false) */
    private static TwinAnalysisResult noDynamicTwin() {
        return new TwinAnalysisResult(
                3, TwinStatus.INITIALISED,
                List.of(), List.of(), false, true);
    }

    /** Normal prediction — same twin version, narrow CI */
    private static PredictionAnalysisResult normalPred(int twinVersion) {
        return new PredictionAnalysisResult(
                UUID.randomUUID(), 0.72, RiskCategory.HIGH,
                new ConfidenceInterval(0.61, 0.83),   // width = 0.22 < 0.35
                List.of(new ContributingFactor("cgm_current", 0.40, RiskDirection.INCREASES_RISK)),
                2, "xgb-v2.0", twinVersion, List.of());
    }

    /** Prediction with wide CI (width > 0.35) */
    private static PredictionAnalysisResult wideCIPred() {
        return new PredictionAnalysisResult(
                UUID.randomUUID(), 0.55, RiskCategory.MODERATE,
                new ConfidenceInterval(0.20, 0.90),   // width = 0.70 > 0.35
                List.of(), 2, "xgb-v2.0", 3, List.of());
    }

    @BeforeEach
    void setUp() {
        agent = new InsightVerificationAgent();
    }

    // ── Test 1: Twin version mismatch detected ────────────────────────────────

    @Test
    void verify_twinVersionMismatch_warningAppended() {
        TwinAnalysisResult twin = activeTwin(5);    // current version = 5
        PredictionAnalysisResult pred = normalPred(3); // prediction against version 3

        List<String> warnings = agent.verify(twin, pred);

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0))
                .contains("Prediction was generated against an earlier twin version");
    }

    // ── Test 2: STALE twin detected ───────────────────────────────────────────

    @Test
    void verify_staleTwin_warningAppended() {
        List<String> warnings = agent.verify(staleTwin(), normalPred(3));

        assertThat(warnings).anyMatch(w -> w.contains("STALE"));
        assertThat(warnings).anyMatch(w -> w.contains("insight confidence is reduced"));
    }

    // ── Test 3: Wide confidence interval detected ─────────────────────────────

    @Test
    void verify_wideCi_warningAppended() {
        List<String> warnings = agent.verify(activeTwin(3), wideCIPred());

        assertThat(warnings).anyMatch(w -> w.contains("Confidence interval is wide"));
        assertThat(warnings).anyMatch(w -> w.contains("prediction uncertainty is elevated"));
    }

    // ── Test 4: Missing dynamic layer detected ────────────────────────────────

    @Test
    void verify_noDynamicLayer_warningAppended() {
        List<String> warnings = agent.verify(noDynamicTwin(), normalPred(3));

        assertThat(warnings).anyMatch(w -> w.contains("No wearable readings"));
        assertThat(warnings).anyMatch(w -> w.contains("static data only"));
    }

    // ── Test 5: Multiple warnings can coexist ─────────────────────────────────

    @Test
    void verify_multipleConditions_allWarningsPresent() {
        // Stale twin + version mismatch + wide CI + no dynamic layer
        TwinAnalysisResult staleTwinV5 = new TwinAnalysisResult(
                5, TwinStatus.STALE, List.of(), List.of(), false, true);
        PredictionAnalysisResult pred = new PredictionAnalysisResult(
                UUID.randomUUID(), 0.55, RiskCategory.MODERATE,
                new ConfidenceInterval(0.10, 0.90),   // width=0.80 > 0.35
                List.of(), 2, "xgb-v2.0", 3,           // twinVersion=3 != current=5
                List.of());

        List<String> warnings = agent.verify(staleTwinV5, pred);

        // Must have all 4 warnings
        assertThat(warnings).hasSizeGreaterThanOrEqualTo(4);
        assertThat(warnings).anyMatch(w -> w.contains("earlier twin version"));
        assertThat(warnings).anyMatch(w -> w.contains("STALE"));
        assertThat(warnings).anyMatch(w -> w.contains("Confidence interval is wide"));
        assertThat(warnings).anyMatch(w -> w.contains("No wearable readings"));
    }

    // ── Test 6: All-clear returns no additional warnings ──────────────────────

    @Test
    void verify_allClear_returnsEmptyList() {
        // Active twin, matching version, narrow CI, has dynamic layer
        List<String> warnings = agent.verify(activeTwin(3), normalPred(3));

        assertThat(warnings).isEmpty();
    }

    // ── Test 7: Verification never blocks insight generation ──────────────────

    @Test
    void verify_withWarnings_doesNotThrow() {
        // Even with many issues, verify() must never throw
        assertThatCode(() -> agent.verify(noDynamicTwin(), wideCIPred()))
                .doesNotThrowAnyException();
    }

    // ── Test 8: Verification does not mutate prediction values ────────────────

    @Test
    void verify_doesNotMutateSpikeProbabilityOrRiskCategory() {
        PredictionAnalysisResult pred = wideCIPred();
        double probBefore = pred.spikeProbability();
        RiskCategory catBefore = pred.riskCategory();
        double ciBefore = pred.confidenceInterval().width();

        agent.verify(activeTwin(3), pred);

        // Records are immutable — values can't change, but assert for documentation
        assertThat(pred.spikeProbability()).isEqualTo(probBefore);
        assertThat(pred.riskCategory()).isEqualTo(catBefore);
        assertThat(pred.confidenceInterval().width()).isEqualTo(ciBefore);
    }

    // ── Test 9: Verification does not mutate provenance ───────────────────────

    @Test
    void verify_doesNotAlterProvenanceOfObservedSignals() {
        TwinAnalysisResult twin = activeTwin(3);
        List<ObservedSignal> signalsBefore = List.copyOf(twin.keyObservedSignals());

        agent.verify(twin, normalPred(3));

        // Signals list is immutable — verify no provenance change
        assertThat(twin.keyObservedSignals()).isEqualTo(signalsBefore);
        twin.keyObservedSignals().forEach(s ->
                assertThat(s.provenance()).isEqualTo(ObservedSignal.PROVENANCE_OBSERVED));
    }
}
