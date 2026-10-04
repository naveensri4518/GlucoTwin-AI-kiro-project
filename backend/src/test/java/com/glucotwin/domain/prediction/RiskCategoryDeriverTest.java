package com.glucotwin.domain.prediction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

class RiskCategoryDeriverTest {

    private final RiskThresholds thresholds = RiskThresholds.DEFAULT;

    @Test
    void derive_returnsLowForProbabilityBelowLowMax() {
        assertThat(RiskCategoryDeriver.derive(0.10, thresholds)).isEqualTo(RiskCategory.LOW);
        assertThat(RiskCategoryDeriver.derive(0.00, thresholds)).isEqualTo(RiskCategory.LOW);
        assertThat(RiskCategoryDeriver.derive(0.29, thresholds)).isEqualTo(RiskCategory.LOW);
    }

    @Test
    void derive_returnsModerateForMidRange() {
        assertThat(RiskCategoryDeriver.derive(0.40, thresholds)).isEqualTo(RiskCategory.MODERATE);
        assertThat(RiskCategoryDeriver.derive(0.50, thresholds)).isEqualTo(RiskCategory.MODERATE);
        assertThat(RiskCategoryDeriver.derive(0.59, thresholds)).isEqualTo(RiskCategory.MODERATE);
    }

    @Test
    void derive_returnsHighForHighRange() {
        assertThat(RiskCategoryDeriver.derive(0.70, thresholds)).isEqualTo(RiskCategory.HIGH);
        assertThat(RiskCategoryDeriver.derive(0.84, thresholds)).isEqualTo(RiskCategory.HIGH);
    }

    @Test
    void derive_returnsCriticalForAboveHighMax() {
        assertThat(RiskCategoryDeriver.derive(0.90, thresholds)).isEqualTo(RiskCategory.CRITICAL);
        assertThat(RiskCategoryDeriver.derive(1.00, thresholds)).isEqualTo(RiskCategory.CRITICAL);
    }

    // Boundary values fall into the upper category
    @Test
    void derive_boundaryAtLowMax_returnsModerate() {
        assertThat(RiskCategoryDeriver.derive(0.30, thresholds)).isEqualTo(RiskCategory.MODERATE);
    }

    @Test
    void derive_boundaryAtModerateMax_returnsHigh() {
        assertThat(RiskCategoryDeriver.derive(0.60, thresholds)).isEqualTo(RiskCategory.HIGH);
    }

    @Test
    void derive_boundaryAtHighMax_returnsCritical() {
        assertThat(RiskCategoryDeriver.derive(0.85, thresholds)).isEqualTo(RiskCategory.CRITICAL);
    }

    @Test
    void derive_withCustomThresholds() {
        var custom = new RiskThresholds(0.20, 0.50, 0.75);
        assertThat(RiskCategoryDeriver.derive(0.15, custom)).isEqualTo(RiskCategory.LOW);
        assertThat(RiskCategoryDeriver.derive(0.20, custom)).isEqualTo(RiskCategory.MODERATE);
        assertThat(RiskCategoryDeriver.derive(0.50, custom)).isEqualTo(RiskCategory.HIGH);
        assertThat(RiskCategoryDeriver.derive(0.75, custom)).isEqualTo(RiskCategory.CRITICAL);
    }

    @Test
    void derive_throwsForProbabilityOutsideUnitInterval() {
        assertThatThrownBy(() -> RiskCategoryDeriver.derive(-0.01, thresholds))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RiskCategoryDeriver.derive(1.01, thresholds))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
