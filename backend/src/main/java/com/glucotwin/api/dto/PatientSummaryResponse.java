package com.glucotwin.api.dto;

import com.glucotwin.domain.patient.Patient;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight patient summary for GET /api/v1/patients (list endpoint).
 * Contains only the minimum information needed for patient selection in the dashboard.
 * No clinical data is included here — fetch EHR separately.
 */
public record PatientSummaryResponse(UUID patientId, Instant createdAt) {

    public static PatientSummaryResponse from(Patient patient) {
        return new PatientSummaryResponse(
                patient.getPatientId().value(),
                patient.getCreatedAt());
    }
}
