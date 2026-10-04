package com.glucotwin.domain.twin;

import com.glucotwin.domain.shared.PatientId;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain record representing a structured EHR upload for a patient.
 * All data is OBSERVED provenance.
 */
public record EhrRecord(
        UUID ehrId,
        PatientId patientId,
        LocalDate dateOfBirth,
        Sex sex,
        Double bmi,
        LocalDate diabetesOnsetDate,
        Double hba1c,
        Double fastingGlucose,
        List<Medication> medications,
        List<LabResult> labResults) {

    public EhrRecord {
        Objects.requireNonNull(ehrId, "ehrId must not be null");
        Objects.requireNonNull(patientId, "patientId must not be null");
        Objects.requireNonNull(dateOfBirth, "dateOfBirth must not be null");
        Objects.requireNonNull(sex, "sex must not be null");
        Objects.requireNonNull(diabetesOnsetDate, "diabetesOnsetDate must not be null");
        medications = medications == null ? List.of() : List.copyOf(medications);
        labResults = labResults == null ? List.of() : List.copyOf(labResults);
    }
}
