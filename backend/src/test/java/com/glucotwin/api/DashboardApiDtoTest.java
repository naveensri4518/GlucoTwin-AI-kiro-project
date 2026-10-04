package com.glucotwin.api;

import com.glucotwin.api.dto.EhrResponse;
import com.glucotwin.api.dto.PatientSummaryResponse;
import com.glucotwin.domain.patient.Patient;
import com.glucotwin.domain.shared.DataProvenance;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.twin.EhrRecord;
import com.glucotwin.domain.twin.Sex;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Lightweight DTO contract tests for the two new dashboard endpoints.
 * No Spring context needed — pure unit tests.
 */
class DashboardApiDtoTest {

    // -------------------------------------------------------------------------
    // EhrResponse
    // -------------------------------------------------------------------------

    @Test
    void ehrResponse_from_mapsAllFieldsCorrectly() {
        PatientId patientId = PatientId.random();
        UUID ehrId = UUID.randomUUID();
        EhrRecord record = new EhrRecord(ehrId, patientId,
                LocalDate.of(1975, 6, 15), Sex.MALE, 28.5,
                LocalDate.of(2015, 3, 1), 7.2, 6.1, List.of(), List.of());

        EhrResponse response = EhrResponse.from(record);

        assertThat(response.ehrId()).isEqualTo(ehrId);
        assertThat(response.patientId()).isEqualTo(patientId.value());
        assertThat(response.dateOfBirth()).isEqualTo(LocalDate.of(1975, 6, 15));
        assertThat(response.sex()).isEqualTo("MALE");
        assertThat(response.bmi()).isEqualTo(28.5);
        assertThat(response.diabetesOnsetDate()).isEqualTo(LocalDate.of(2015, 3, 1));
        assertThat(response.hba1c()).isEqualTo(7.2);
        assertThat(response.fastingGlucose()).isEqualTo(6.1);
    }

    @Test
    void ehrResponse_dataProvenanceIsAlwaysOBSERVED() {
        PatientId patientId = PatientId.random();
        EhrRecord record = new EhrRecord(UUID.randomUUID(), patientId,
                LocalDate.of(1980, 1, 1), Sex.FEMALE, null,
                LocalDate.of(2020, 1, 1), null, null, List.of(), List.of());

        EhrResponse response = EhrResponse.from(record);

        assertThat(response.dataProvenance()).isEqualTo(DataProvenance.OBSERVED.name());
    }

    @Test
    void ehrResponse_nullOptionalFieldsAreAllowed() {
        PatientId patientId = PatientId.random();
        EhrRecord record = new EhrRecord(UUID.randomUUID(), patientId,
                LocalDate.of(1960, 5, 20), Sex.OTHER, null,  // bmi null
                LocalDate.of(2010, 3, 1), null, null,        // hba1c and fastingGlucose null
                List.of(), List.of());

        EhrResponse response = EhrResponse.from(record);

        assertThat(response.bmi()).isNull();
        assertThat(response.hba1c()).isNull();
        assertThat(response.fastingGlucose()).isNull();
    }

    // -------------------------------------------------------------------------
    // PatientSummaryResponse
    // -------------------------------------------------------------------------

    @Test
    void patientSummaryResponse_from_mapsPatientIdAndCreatedAt() {
        PatientId patientId = PatientId.random();
        Instant createdAt = Instant.now();
        Patient patient = new Patient(patientId, createdAt);

        PatientSummaryResponse response = PatientSummaryResponse.from(patient);

        assertThat(response.patientId()).isEqualTo(patientId.value());
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void patientSummaryResponse_doesNotExposeNonExistentSensitiveFields() {
        // PatientSummaryResponse should only have patientId + createdAt (2 fields)
        // This ensures we don't accidentally add clinical data to the list endpoint
        var components = PatientSummaryResponse.class.getRecordComponents();
        assertThat(components).hasSize(2);
        var fieldNames = java.util.Arrays.stream(components)
                .map(c -> c.getName())
                .toList();
        assertThat(fieldNames).containsExactlyInAnyOrder("patientId", "createdAt");
    }
}
