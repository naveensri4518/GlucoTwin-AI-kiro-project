package com.glucotwin.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * Request body for POST /api/v1/patients/{id}/simulations.
 * At least one field must be non-null (validated in use case).
 */
public record SimulationRequest(
        @DecimalMin(value = "0.0",   message = "mealCarbsGrams must be >= 0")
        @DecimalMax(value = "500.0", message = "mealCarbsGrams must be <= 500")
        Double mealCarbsGrams,

        String activityLevel,     // SEDENTARY | LIGHT | MODERATE | VIGOROUS

        Boolean medicationTaken
) {}
