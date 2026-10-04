package com.glucotwin.domain.shared;

import java.util.Objects;
import java.util.UUID;

/** Typed wrapper for a patient UUID — prevents raw UUID misuse across domain boundaries. */
public record PatientId(UUID value) {

    public PatientId {
        Objects.requireNonNull(value, "PatientId value must not be null");
    }

    public static PatientId of(UUID value) {
        return new PatientId(value);
    }

    public static PatientId of(String value) {
        return new PatientId(UUID.fromString(value));
    }

    public static PatientId random() {
        return new PatientId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
