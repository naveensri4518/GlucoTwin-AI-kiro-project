package com.glucotwin.domain.twin;

import java.time.Instant;
import java.util.Objects;

public record LabResult(String type, double value, String unit, Instant recordedAt) {
    public LabResult {
        Objects.requireNonNull(type, "lab result type must not be null");
        Objects.requireNonNull(unit, "unit must not be null");
        Objects.requireNonNull(recordedAt, "recordedAt must not be null");
    }
}
