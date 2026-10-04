package com.glucotwin.domain.patient;

import com.glucotwin.domain.shared.PatientId;

import java.time.Instant;
import java.util.Objects;

/** Domain entity representing a registered patient (the identity anchor). */
public class Patient {

    private final PatientId patientId;
    private final Instant createdAt;
    private Instant updatedAt;

    public Patient(PatientId patientId, Instant createdAt) {
        this.patientId = Objects.requireNonNull(patientId);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = createdAt;
    }

    public static Patient create(PatientId patientId) {
        return new Patient(patientId, Instant.now());
    }

    public PatientId getPatientId() { return patientId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touch() { this.updatedAt = Instant.now(); }
}
