package com.glucotwin.domain.insight;

import java.util.Objects;

/**
 * A single observed clinical signal extracted from the Digital Twin's dynamic layer.
 * Provenance is always OBSERVED — never derived from predictions or simulations.
 */
public record ObservedSignal(
        String name,
        String value,
        String unit,
        String provenance) {

    public static final String PROVENANCE_OBSERVED = "OBSERVED";

    public ObservedSignal {
        Objects.requireNonNull(name, "signal name must not be null");
        Objects.requireNonNull(provenance, "provenance must not be null");
        if (!PROVENANCE_OBSERVED.equals(provenance)) {
            throw new IllegalArgumentException(
                    "ObservedSignal provenance must be OBSERVED, got: " + provenance);
        }
    }

    public static ObservedSignal of(String name, String value, String unit) {
        return new ObservedSignal(name, value, unit, PROVENANCE_OBSERVED);
    }

    public static ObservedSignal of(String name, String value) {
        return new ObservedSignal(name, value, null, PROVENANCE_OBSERVED);
    }
}
