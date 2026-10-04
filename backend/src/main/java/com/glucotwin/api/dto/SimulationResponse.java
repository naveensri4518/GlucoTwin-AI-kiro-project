package com.glucotwin.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.glucotwin.domain.prediction.ContributingFactor;
import com.glucotwin.domain.simulation.SimulationResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response body for POST /api/v1/patients/{id}/simulations.
 * dataProvenance is always "SIMULATED" — enforced in the domain.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SimulationResponse(
        UUID simulationId,
        UUID patientId,
        Instant simulatedAt,
        int predictionHorizonHours,
        double spikeProbability,
        String riskCategory,
        ConfidenceIntervalDto confidenceInterval,
        List<ContributingFactorDto> topContributingFactors,
        String dataProvenance,
        int twinStateVersion,
        String modelVersion,
        Map<String, Object> scenarioInputs,
        Double deltaVsBaseline,
        List<String> dataQualityWarnings,
        String disclaimer) {

    public record ConfidenceIntervalDto(double low, double high) {}
    public record ContributingFactorDto(String factorName, double contribution, String direction) {}

    public static SimulationResponse from(SimulationResult r) {
        var ci = new ConfidenceIntervalDto(
                r.confidenceInterval().low(),
                r.confidenceInterval().high());

        List<ContributingFactorDto> factors = r.topContributingFactors().stream()
                .map(f -> new ContributingFactorDto(
                        f.factorName(), f.contribution(), f.direction().name()))
                .toList();

        // Flatten scenario inputs to a simple map for the JSON response
        var sc = r.scenarioInputs();
        Map<String, Object> scenarioMap = new java.util.LinkedHashMap<>();
        if (sc.mealCarbsGrams() != null) scenarioMap.put("mealCarbsGrams", sc.mealCarbsGrams());
        if (sc.activityLevel() != null) scenarioMap.put("activityLevel", sc.activityLevel().name());
        if (sc.medicationTaken() != null) scenarioMap.put("medicationTaken", sc.medicationTaken());

        return new SimulationResponse(
                r.simulationId(),
                r.patientId().value(),
                r.simulatedAt(),
                r.predictionHorizonHours(),
                r.spikeProbability(),
                r.riskCategory().name(),
                ci,
                factors,
                r.dataProvenance().name(),   // always "SIMULATED"
                r.twinStateVersion(),
                r.modelVersion(),
                scenarioMap,
                r.deltaVsBaseline(),
                r.dataQualityWarnings(),
                SimulationResult.SAFETY_DISCLAIMER);
    }
}
