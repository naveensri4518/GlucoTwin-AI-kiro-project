package com.glucotwin.domain.twin;

import com.glucotwin.domain.shared.PatientId;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable snapshot of a DigitalTwinState taken at a specific moment.
 * Used as the input to the prediction engine — must not be affected by
 * subsequent state mutations.
 */
public record TwinStateSnapshot(
        PatientId patientId,
        int twinVersion,
        TwinStatus status,
        StaticLayer staticLayer,
        DynamicLayer dynamicLayer,
        Instant snapshotTakenAt) {

    public TwinStateSnapshot {
        Objects.requireNonNull(patientId, "patientId must not be null");
        Objects.requireNonNull(snapshotTakenAt, "snapshotTakenAt must not be null");
        if (twinVersion < 1) throw new IllegalArgumentException("twinVersion must be >= 1");
    }

    /** Factory — creates an immutable snapshot from the current state. */
    public static TwinStateSnapshot from(DigitalTwinState state) {
        Objects.requireNonNull(state, "state must not be null");
        return new TwinStateSnapshot(
                state.getPatientId(),
                state.getTwinVersion(),
                state.getStatus(),
                state.getStaticLayer(),
                state.getDynamicLayer(),
                Instant.now());
    }

    /** Convenience: returns true if the glucose reading is present (required for prediction). */
    public boolean hasGlucoseReading() {
        return dynamicLayer != null && dynamicLayer.glucoseReading() != null;
    }

    /** Convenience: returns the current glucose reading or null. */
    public Double getGlucoseReading() {
        return dynamicLayer != null ? dynamicLayer.glucoseReading() : null;
    }
}
