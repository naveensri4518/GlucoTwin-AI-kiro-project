package com.glucotwin.domain.simulation;

import com.glucotwin.domain.prediction.ConfidenceInterval;
import com.glucotwin.domain.prediction.ContributingFactor;
import com.glucotwin.domain.prediction.RiskCategory;
import com.glucotwin.domain.shared.DataProvenance;
import com.glucotwin.domain.shared.PatientId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing the output of a what-if glucose spike simulation.
 *
 * <p>dataProvenance is always SIMULATED — this result must never be stored in
 * the glucose_predictions table and must always be labelled as hypothetical.
 *
 * <p>Safety: this is a hypothetical what-if scenario. Not a medical recommendation.
 */
public record SimulationResult(
        UUID simulationId,
        PatientId patientId,
        int twinStateVersion,
        SimulationScenario scenarioInputs,
        double spikeProbability,
        RiskCategory riskCategory,
        ConfidenceInterval confidenceInterval,
        List<ContributingFactor> topContributingFactors,
        int predictionHorizonHours,
        String modelVersion,
        List<String> dataQualityWarnings,
        /** Delta vs the REAL current prediction, null if no current prediction exists. */
        Double deltaVsBaseline,
        Instant simulatedAt,
        DataProvenance dataProvenance) {

    public static final String SAFETY_DISCLAIMER =
            "This is a hypothetical what-if scenario. Not a medical recommendation.";

    public SimulationResult {
        Objects.requireNonNull(simulationId, "simulationId must not be null");
        Objects.requireNonNull(patientId, "patientId must not be null");
        Objects.requireNonNull(scenarioInputs, "scenarioInputs must not be null");
        Objects.requireNonNull(riskCategory, "riskCategory must not be null");
        Objects.requireNonNull(confidenceInterval, "confidenceInterval must not be null");
        Objects.requireNonNull(modelVersion, "modelVersion must not be null");
        Objects.requireNonNull(simulatedAt, "simulatedAt must not be null");

        // Enforce SIMULATED provenance — invariant, never negotiable
        if (dataProvenance != DataProvenance.SIMULATED) {
            throw new IllegalArgumentException(
                    "SimulationResult dataProvenance must be SIMULATED, got: " + dataProvenance);
        }
        // Clamp probability defensively
        spikeProbability = Math.max(0.0, Math.min(1.0, spikeProbability));
        topContributingFactors = topContributingFactors == null
                ? List.of() : List.copyOf(topContributingFactors);
        dataQualityWarnings = dataQualityWarnings == null
                ? List.of() : List.copyOf(dataQualityWarnings);
    }
}
