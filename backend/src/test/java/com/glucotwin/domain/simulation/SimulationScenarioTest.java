package com.glucotwin.domain.simulation;

import com.glucotwin.domain.twin.ActivityLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class SimulationScenarioTest {

    @Test
    void hasAnyInput_trueWhenCarbsProvided() {
        assertThat(new SimulationScenario(60.0, null, null).hasAnyInput()).isTrue();
    }

    @Test
    void hasAnyInput_trueWhenActivityProvided() {
        assertThat(new SimulationScenario(null, ActivityLevel.VIGOROUS, null).hasAnyInput()).isTrue();
    }

    @Test
    void hasAnyInput_trueWhenMedicationProvided() {
        assertThat(new SimulationScenario(null, null, false).hasAnyInput()).isTrue();
    }

    @Test
    void hasAnyInput_falseWhenAllNull() {
        assertThat(new SimulationScenario(null, null, null).hasAnyInput()).isFalse();
    }

    @Test
    void mealCarbsGrams_rejectsNegative() {
        assertThatThrownBy(() -> new SimulationScenario(-1.0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mealCarbsGrams_rejectsAboveMax() {
        assertThatThrownBy(() -> new SimulationScenario(501.0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mealCarbsGrams_acceptsBoundaryValues() {
        assertThatNoException().isThrownBy(() -> new SimulationScenario(0.0, null, null));
        assertThatNoException().isThrownBy(() -> new SimulationScenario(500.0, null, null));
    }
}
