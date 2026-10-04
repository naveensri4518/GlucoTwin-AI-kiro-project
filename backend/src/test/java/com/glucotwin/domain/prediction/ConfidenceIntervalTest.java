package com.glucotwin.domain.prediction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ConfidenceIntervalTest {

    @Test
    void constructor_acceptsValidInterval() {
        var ci = new ConfidenceInterval(0.3, 0.8);
        assertThat(ci.low()).isEqualTo(0.3);
        assertThat(ci.high()).isEqualTo(0.8);
        assertThat(ci.width()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void constructor_throwsWhenLowGreaterThanHigh() {
        assertThatThrownBy(() -> new ConfidenceInterval(0.8, 0.3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("low");
    }

    @Test
    void constructor_throwsWhenLowEqualsHigh() {
        assertThatThrownBy(() -> new ConfidenceInterval(0.5, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("width");
    }

    @Test
    void constructor_throwsWhenLowBelowZero() {
        assertThatThrownBy(() -> new ConfidenceInterval(-0.1, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_throwsWhenHighAboveOne() {
        assertThatThrownBy(() -> new ConfidenceInterval(0.5, 1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void width_isPositive() {
        var ci = new ConfidenceInterval(0.2, 0.9);
        assertThat(ci.width()).isGreaterThan(0.0);
    }
}
