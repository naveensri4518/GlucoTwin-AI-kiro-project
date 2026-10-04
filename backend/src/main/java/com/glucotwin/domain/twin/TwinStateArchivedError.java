package com.glucotwin.domain.twin;

/** Thrown when a mutation is attempted on an ARCHIVED DigitalTwinState. */
public class TwinStateArchivedError extends RuntimeException {
    public TwinStateArchivedError(String patientId) {
        super("Cannot mutate archived DigitalTwinState for patient: " + patientId);
    }
}
