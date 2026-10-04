package com.glucotwin.infrastructure.persistence;

import com.glucotwin.domain.prediction.ConfidenceInterval;
import com.glucotwin.domain.prediction.ContributingFactor;
import com.glucotwin.domain.prediction.RiskCategory;
import com.glucotwin.domain.prediction.RiskDirection;
import com.glucotwin.domain.shared.DataProvenance;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.simulation.SimulationResult;
import com.glucotwin.domain.simulation.SimulationScenario;
import com.glucotwin.domain.twin.ActivityLevel;
import com.glucotwin.infrastructure.persistence.entity.SimulationResultJpaEntity;
import com.glucotwin.infrastructure.persistence.repository.SimulationResultJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * Persists SimulationResult to the simulations table.
 * Never touches glucose_predictions — enforced by using a separate entity and table.
 */
@Repository
@RequiredArgsConstructor
public class JpaSimulationRepository {

    private final SimulationResultJpaRepository jpaRepo;

    public SimulationResult save(SimulationResult result) {
        SimulationResultJpaEntity entity = toEntity(result);
        jpaRepo.save(entity);
        return result; // domain object is immutable — return as-is
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private SimulationResultJpaEntity toEntity(SimulationResult r) {
        var entity = new SimulationResultJpaEntity();
        entity.setSimulationId(r.simulationId());
        entity.setPatientId(r.patientId().value());
        entity.setTwinStateVersion(r.twinStateVersion());
        entity.setSpikeProbability(r.spikeProbability());
        entity.setRiskCategory(r.riskCategory().name());
        entity.setCiLow(r.confidenceInterval().low());
        entity.setCiHigh(r.confidenceInterval().high());
        entity.setPredictionHorizonHrs(r.predictionHorizonHours());
        entity.setModelVersion(r.modelVersion());
        entity.setDataQualityWarnings(r.dataQualityWarnings().toArray(new String[0]));
        entity.setDeltaVsBaseline(r.deltaVsBaseline());
        entity.setSimulatedAt(r.simulatedAt());
        entity.setDataProvenance("SIMULATED");

        // Scenario inputs as flat JSON map
        Map<String, Object> scenario = new LinkedHashMap<>();
        SimulationScenario sc = r.scenarioInputs();
        if (sc.mealCarbsGrams() != null) scenario.put("mealCarbsGrams", sc.mealCarbsGrams());
        if (sc.activityLevel() != null) scenario.put("activityLevel", sc.activityLevel().name());
        if (sc.medicationTaken() != null) scenario.put("medicationTaken", sc.medicationTaken());
        entity.setScenarioInputs(scenario);

        // Contributing factors as list-of-maps
        List<Map<String, Object>> factors = new ArrayList<>();
        for (ContributingFactor f : r.topContributingFactors()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("factorName", f.factorName());
            m.put("contribution", f.contribution());
            m.put("direction", f.direction().name());
            factors.add(m);
        }
        entity.setContributingFactors(factors);

        return entity;
    }
}
