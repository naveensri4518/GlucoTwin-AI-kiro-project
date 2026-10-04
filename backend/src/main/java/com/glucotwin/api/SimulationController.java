package com.glucotwin.api;

import com.glucotwin.api.dto.SimulationRequest;
import com.glucotwin.api.dto.SimulationResponse;
import com.glucotwin.application.SimulatePredictionUseCase;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.shared.ValidationException;
import com.glucotwin.domain.simulation.SimulationResult;
import com.glucotwin.domain.simulation.SimulationScenario;
import com.glucotwin.domain.twin.ActivityLevel;
import com.glucotwin.infrastructure.persistence.JpaSimulationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for what-if glucose simulation.
 *
 * <p>Every response is labelled dataProvenance = SIMULATED and includes the safety disclaimer.
 * This endpoint is for clinical decision support only — not a diagnostic system.
 */
@RestController
@RequestMapping("/api/v1/patients/{patientId}/simulations")
@RequiredArgsConstructor
@Tag(name = "Simulation")
public class SimulationController {

    private final SimulatePredictionUseCase simulatePredictionUseCase;
    private final JpaSimulationRepository simulationRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN')")
    @Operation(
            summary = "Run a what-if glucose spike simulation",
            description = "Applies hypothetical scenario inputs to the patient's current Digital Twin " +
                          "state and predicts the glucose spike probability for that scenario. " +
                          "Results are labelled SIMULATED — this is a hypothetical scenario, " +
                          "not a medical recommendation. The real Digital Twin is never modified.")
    public ResponseEntity<SimulationResponse> simulate(
            @PathVariable UUID patientId,
            @Valid @RequestBody SimulationRequest request) {

        ActivityLevel activityLevel = null;
        if (request.activityLevel() != null) {
            try {
                activityLevel = ActivityLevel.valueOf(request.activityLevel().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ValidationException("activityLevel", "INVALID_VALUE",
                        "activityLevel must be one of: SEDENTARY, LIGHT, MODERATE, VIGOROUS");
            }
        }

        SimulationScenario scenario = new SimulationScenario(
                request.mealCarbsGrams(),
                activityLevel,
                request.medicationTaken());

        SimulationResult result = simulatePredictionUseCase.execute(PatientId.of(patientId), scenario);

        // Persist to simulations table (never to glucose_predictions)
        simulationRepository.save(result);

        return ResponseEntity.ok(SimulationResponse.from(result));
    }
}
