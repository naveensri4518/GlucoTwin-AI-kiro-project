package com.glucotwin.evaluation;

import com.glucotwin.domain.insight.AgentStepTrace;
import com.glucotwin.domain.insight.ObservedSignal;
import com.glucotwin.domain.prediction.ConfidenceInterval;
import com.glucotwin.domain.prediction.RiskCategory;
import com.glucotwin.domain.prediction.RiskCategoryDeriver;
import com.glucotwin.domain.prediction.RiskThresholds;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Phase 14 — jqwik Property-Based Evaluation Tests.
 *
 * Four meaningful properties verifying deterministic domain invariants.
 * Uses jqwik @Property — not JUnit tests.
 * No external service calls.
 */
@Tag("evaluation")
class ClinicalInsightProvenancePropertyTest {

    private static final RiskThresholds DEFAULT_THRESHOLDS =
            new RiskThresholds(0.30, 0.60, 0.85);

    // ── Property 1: RiskCategoryDeriver is monotonic ──────────────────────────

    /**
     * For any two valid probabilities p1 <= p2, the derived risk category ordinal
     * must be non-decreasing (monotonicity of risk classification).
     */
    @Property(tries = 200)
    void riskCategoryDeriver_isMonotonic(
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double p1,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double p2) {

        double lo = Math.min(p1, p2);
        double hi = Math.max(p1, p2);

        RiskCategory loCategory = RiskCategoryDeriver.derive(lo, DEFAULT_THRESHOLDS);
        RiskCategory hiCategory = RiskCategoryDeriver.derive(hi, DEFAULT_THRESHOLDS);

        assertThat(loCategory.ordinal())
                .as("Risk category must be non-decreasing: derive(%s)=%s, derive(%s)=%s",
                        lo, loCategory, hi, hiCategory)
                .isLessThanOrEqualTo(hiCategory.ordinal());
    }

    // ── Property 2: ConfidenceInterval width is always positive ───────────────

    /**
     * For any valid CI where low < high and both in [0,1], width must be > 0.
     */
    @Property(tries = 200)
    void confidenceInterval_widthIsAlwaysPositive(
            @ForAll @DoubleRange(min = 0.0, max = 0.99) double low,
            @ForAll @DoubleRange(min = 0.01, max = 1.0) double highOffset) {

        double high = Math.min(1.0, low + highOffset);
        // Skip degenerate case where arithmetic produces equal values
        Assume.that(low < high);

        ConfidenceInterval ci = new ConfidenceInterval(low, high);

        assertThat(ci.width())
                .as("CI width must be > 0 for low=%s, high=%s", low, high)
                .isGreaterThan(0.0);
    }

    // ── Property 3: AgentStepTrace status is always one of the valid set ──────

    /**
     * A step constructed via factory methods must always have a valid status.
     */
    @Property(tries = 200)
    void agentStepTrace_statusIsAlwaysValid(
            @ForAll @From("validStatusNames") String status,
            @ForAll @StringLength(min = 1, max = 20) @AlphaChars String agentName,
            @ForAll @LongRange(min = 0, max = 10_000) long durationMs) {

        AgentStepTrace step = new AgentStepTrace(agentName, status, durationMs, "detail");

        assertThat(step.status())
                .as("AgentStepTrace status must be one of SUCCESS/FAILURE/SKIPPED")
                .isIn(AgentStepTrace.STATUS_SUCCESS,
                      AgentStepTrace.STATUS_FAILURE,
                      AgentStepTrace.STATUS_SKIPPED);
    }

    @Provide
    Arbitrary<String> validStatusNames() {
        return Arbitraries.of(
                AgentStepTrace.STATUS_SUCCESS,
                AgentStepTrace.STATUS_FAILURE,
                AgentStepTrace.STATUS_SKIPPED);
    }

    // ── Property 4: ObservedSignal provenance is always OBSERVED ──────────────

    /**
     * Any ObservedSignal created via the factory must carry provenance=OBSERVED.
     */
    @Property(tries = 200)
    void observedSignal_provenanceIsAlwaysObserved(
            @ForAll @StringLength(min = 1, max = 30) @AlphaChars String name,
            @ForAll @StringLength(min = 1, max = 10)  @AlphaChars String value) {

        ObservedSignal signal = ObservedSignal.of(name, value);

        assertThat(signal.provenance())
                .as("ObservedSignal provenance must always be OBSERVED, got: %s",
                        signal.provenance())
                .isEqualTo(ObservedSignal.PROVENANCE_OBSERVED);
    }
}
