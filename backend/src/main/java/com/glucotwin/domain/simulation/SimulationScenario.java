package com.glucotwin.domain.simulation;

import com.glucotwin.domain.twin.ActivityLevel;

import java.util.Objects;

/**
 * Value object representing the hypothetical scenario inputs for a what-if simulation.
 * All fields are optional — only the provided fields mutate the cloned snapshot.
 */
public record SimulationScenario(
        Double mealCarbsGrams,
        ActivityLevel activityLevel,
        Boolean medicationTaken) {

    public SimulationScenario {
        if (mealCarbsGrams != null && (mealCarbsGrams < 0 || mealCarbsGrams > 500)) {
            throw new IllegalArgumentException(
                    "mealCarbsGrams must be in [0, 500], got: " + mealCarbsGrams);
        }
    }

    /** True when at least one scenario input was provided. */
    public boolean hasAnyInput() {
        return mealCarbsGrams != null || activityLevel != null || medicationTaken != null;
    }
}
