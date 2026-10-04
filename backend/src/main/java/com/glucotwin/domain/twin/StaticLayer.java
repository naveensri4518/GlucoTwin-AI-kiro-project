package com.glucotwin.domain.twin;

import com.glucotwin.domain.shared.DataProvenance;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * The EHR-derived static layer of a Digital Twin.
 * Provenance is always OBSERVED.
 * Changes infrequently — updated on EHR upload only.
 */
public record StaticLayer(
        LocalDate dateOfBirth,
        Sex sex,
        Double bmi,
        LocalDate diabetesOnsetDate,
        Double hba1c,
        Double fastingGlucose,
        List<Medication> medications,
        List<LabResult> labResults,
        DataProvenance dataProvenance) {

    /** Factory for a new empty static layer (before first EHR upload). */
    public static StaticLayer empty() {
        return new StaticLayer(null, null, null, null, null, null,
                List.of(), List.of(), DataProvenance.OBSERVED);
    }

    public StaticLayer {
        // dataProvenance must always be OBSERVED for the static layer
        if (dataProvenance != null && dataProvenance != DataProvenance.OBSERVED) {
            throw new IllegalArgumentException("StaticLayer dataProvenance must be OBSERVED");
        }
        medications = medications == null ? List.of() : List.copyOf(medications);
        labResults = labResults == null ? List.of() : List.copyOf(labResults);
    }

    /** Returns a copy with the given EHR fields applied. */
    public StaticLayer withEhrUpdate(
            LocalDate dateOfBirth,
            Sex sex,
            Double bmi,
            LocalDate diabetesOnsetDate,
            Double hba1c,
            Double fastingGlucose,
            List<Medication> medications,
            List<LabResult> labResults) {
        return new StaticLayer(
                dateOfBirth, sex, bmi, diabetesOnsetDate,
                hba1c, fastingGlucose,
                Objects.requireNonNullElse(medications, List.of()),
                Objects.requireNonNullElse(labResults, List.of()),
                DataProvenance.OBSERVED);
    }
}
