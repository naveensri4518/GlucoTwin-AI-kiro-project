package com.glucotwin.domain.shared;

import java.util.Objects;
import java.util.UUID;

/** Typed wrapper for a prediction UUID. */
public record PredictionId(UUID value) {

    public PredictionId {
        Objects.requireNonNull(value, "PredictionId value must not be null");
    }

    public static PredictionId of(UUID value) {
        return new PredictionId(value);
    }

    public static PredictionId of(String value) {
        return new PredictionId(UUID.fromString(value));
    }

    public static PredictionId random() {
        return new PredictionId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
