package com.glucotwin.application;

import com.glucotwin.domain.prediction.*;
import com.glucotwin.domain.shared.DataProvenance;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.shared.ResourceNotFoundException;
import com.glucotwin.domain.shared.ValidationException;
import com.glucotwin.domain.simulation.SimulationResult;
import com.glucotwin.domain.simulation.SimulationScenario;
import com.glucotwin.domain.twin.*;
import com.glucotwin.infrastructure.config.GlucoTwinProperties;
import com.glucotwin.infrastructure.observability.PatientIdRef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runs a what-if glucose spike simulation for a patient.
 *
 * <p>Design per spec §15.4:
 * <ol>
 *   <li>Load the current DigitalTwinState (read-only).</li>
 *   <li>Take an immutable TwinStateSnapshot.</li>
 *   <li>Clone the snapshot and apply only the hypothetical scenario mutations.</li>
 *   <li>Call the existing PredictionModelPort.predict() on the mutated snapshot.</li>
 *   <li>Return a SimulationResult with dataProvenance = SIMULATED.</li>
 * </ol>
 *
 * <p>The real DigitalTwinState is NEVER modified.
 * The result is NEVER stored in glucose_predictions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimulatePredictionUseCase {

    private final DigitalTwinRepository digitalTwinRepository;
    private final PredictionRepository predictionRepository;
    private final PredictionModelPort predictionModelPort;
    private final GlucoTwinProperties properties;

    @Transactional(readOnly = true)
    public SimulationResult execute(PatientId patientId, SimulationScenario scenario) {

        // Guard: at least one scenario input must be provided
        if (!scenario.hasAnyInput()) {
            throw new ValidationException("scenario", "EMPTY_SCENARIO",
                    "At least one scenario input (mealCarbsGrams, activityLevel, medicationTaken) must be provided");
        }

        // 1. Load current twin state (read-only — never modified)
        DigitalTwinState twin = digitalTwinRepository.findByPatientId(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("DigitalTwinState", patientId.toString()));

        if (twin.getStatus() == TwinStatus.ARCHIVED) {
            throw new TwinStateArchivedError(patientId.toString());
        }

        // 2. Take immutable snapshot of real state
        TwinStateSnapshot realSnapshot = twin.snapshot();

        if (!realSnapshot.hasGlucoseReading()) {
            throw new GlucoseReadingRequiredException(patientId.toString());
        }

        // 3. Build mutated snapshot (clone + apply scenario — real state unchanged)
        TwinStateSnapshot mutatedSnapshot = applyScenario(realSnapshot, scenario);

        log.info("SIMULATION_TRIGGERED patientId={} scenario=[carbs={}, activity={}, medication={}]",
                PatientIdRef.hash(patientId),
                scenario.mealCarbsGrams(),
                scenario.activityLevel(),
                scenario.medicationTaken());

        // 4. Run prediction on the MUTATED snapshot via the existing port
        PredictionResult result = predictionModelPort.predict(mutatedSnapshot);

        // 5. Derive risk category
        RiskThresholds thresholds = properties.getRiskThresholds();
        RiskCategory riskCategory = RiskCategoryDeriver.derive(result.spikeProbability(), thresholds);

        // 6. Optionally compute delta vs the most recent REAL completed prediction
        Double deltaVsBaseline = computeDeltaVsBaseline(patientId, result.spikeProbability());

        // 7. Collect data quality warnings
        List<String> warnings = new ArrayList<>();
        if (twin.getStatus() == TwinStatus.STALE) {
            warnings.add("STALE_WEARABLE_DATA");
        }
        for (String imputed : result.imputedFields()) {
            warnings.add("IMPUTED_FIELD:" + imputed);
        }

        SimulationResult simulationResult = new SimulationResult(
                UUID.randomUUID(),
                patientId,
                realSnapshot.twinVersion(),
                scenario,
                result.spikeProbability(),
                riskCategory,
                result.confidenceInterval(),
                result.topContributingFactors(),
                PredictionRecord.PREDICTION_HORIZON_HOURS,
                result.modelVersion(),
                warnings,
                deltaVsBaseline,
                Instant.now(),
                DataProvenance.SIMULATED   // invariant — enforced in SimulationResult constructor
        );

        log.info("SIMULATION_COMPLETED patientId={} spikeProbability={} riskCategory={} deltaVsBaseline={}",
                PatientIdRef.hash(patientId),
                result.spikeProbability(),
                riskCategory,
                deltaVsBaseline);

        return simulationResult;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a NEW immutable TwinStateSnapshot with only the scenario fields changed.
     * The real snapshot's patientId, twinVersion, staticLayer, and cgmHistory are preserved.
     * The real DigitalTwinState is never touched.
     */
    private TwinStateSnapshot applyScenario(TwinStateSnapshot real, SimulationScenario scenario) {
        DynamicLayer dl = real.dynamicLayer();
        if (dl == null) {
            dl = DynamicLayer.empty();
        }

        // Determine mutated values: scenario overrides, otherwise keep current
        ActivityLevel newActivity = scenario.activityLevel() != null
                ? scenario.activityLevel()
                : dl.activityLevel();

        // Meal carbs → approximate glucose impact for feature engineering context
        // We do NOT modify glucose readings directly (that would be fabricating sensor data).
        // Instead, step count is used as a proxy for activity; activityLevel is the primary mutation.
        // medicationTaken is conveyed via the dataQualityWarnings on the snapshot.

        java.util.Set<String> scenarioWarnings = new java.util.HashSet<>(dl.dataQualityWarnings());
        if (Boolean.FALSE.equals(scenario.medicationTaken())) {
            scenarioWarnings.add("SCENARIO_MEDICATION_NOT_TAKEN");
        }
        if (scenario.mealCarbsGrams() != null) {
            scenarioWarnings.add("SCENARIO_MEAL_CARBS_" + Math.round(scenario.mealCarbsGrams()) + "G");
        }

        // Build mutated DynamicLayer — keeps real glucose reading and CGM history intact
        DynamicLayer mutatedDynamic = new DynamicLayer(
                dl.glucoseReading(),         // unchanged — this is the real observed value
                dl.heartRate(),
                dl.hrv(),
                dl.sleepDuration(),
                dl.sleepStage(),
                dl.stepCount(),
                newActivity,                 // mutated by scenario
                dl.eventTimestamp(),
                dl.cgmHistory(),             // unchanged — real history
                java.util.Collections.unmodifiableSet(scenarioWarnings),
                dl.dataProvenance());        // always OBSERVED on DynamicLayer

        // Return a new immutable snapshot — original snapshot is untouched
        return new TwinStateSnapshot(
                real.patientId(),
                real.twinVersion(),
                real.status(),
                real.staticLayer(),
                mutatedDynamic,
                Instant.now());
    }

    /**
     * Looks up the most recent COMPLETED real prediction for this patient.
     * Returns delta = simulatedProbability - realProbability, or null if none exists.
     * Never uses the MCP hardcoded baseline.
     */
    private Double computeDeltaVsBaseline(PatientId patientId, double simulatedProb) {
        try {
            var page = predictionRepository.findByPatientId(
                    patientId,
                    org.springframework.data.domain.PageRequest.of(0, 1,
                            org.springframework.data.domain.Sort.by(
                                    org.springframework.data.domain.Sort.Direction.DESC, "predictedAt")));

            return page.getContent().stream()
                    .filter(p -> p.getStatus() == PredictionStatus.COMPLETED
                            && p.getDataProvenance() == DataProvenance.PREDICTED
                            && p.getSpikeProbability() != null)
                    .findFirst()
                    .map(p -> simulatedProb - p.getSpikeProbability())
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Could not compute deltaVsBaseline for patient {}: {}",
                    PatientIdRef.hash(patientId), e.getMessage());
            return null;
        }
    }
}
