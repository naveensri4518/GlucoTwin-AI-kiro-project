package com.glucotwin.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.glucotwin.domain.twin.EhrRecord;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for GET /api/v1/patients/{id}/ehr
 * All fields are OBSERVED provenance (EHR data).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EhrResponse(
        UUID ehrId,
        UUID patientId,
        LocalDate dateOfBirth,
        String sex,
        Double bmi,
        LocalDate diabetesOnsetDate,
        Double hba1c,
        Double fastingGlucose,
        String dataProvenance) {

    public static EhrResponse from(EhrRecord record) {
        return new EhrResponse(
                record.ehrId(),
                record.patientId().value(),
                record.dateOfBirth(),
                record.sex().name(),
                record.bmi(),
                record.diabetesOnsetDate(),
                record.hba1c(),
                record.fastingGlucose(),
                "OBSERVED");
    }
}
