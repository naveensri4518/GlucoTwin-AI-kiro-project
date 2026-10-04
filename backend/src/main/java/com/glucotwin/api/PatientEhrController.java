package com.glucotwin.api;

import com.glucotwin.api.dto.EhrResponse;
import com.glucotwin.api.dto.EhrUploadRequest;
import com.glucotwin.application.GetEhrUseCase;
import com.glucotwin.application.IngestEhrUseCase;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.shared.ValidationException;
import com.glucotwin.domain.twin.EhrRecord;
import com.glucotwin.domain.twin.Sex;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients/{patientId}/ehr")
@RequiredArgsConstructor
@Tag(name = "EHR")
public class PatientEhrController {

    private final IngestEhrUseCase ingestEhrUseCase;
    private final GetEhrUseCase getEhrUseCase;

    @PostMapping
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN')")
    @Operation(summary = "Upload or replace EHR data for a patient")
    public ResponseEntity<Map<String, Object>> upload(
            @PathVariable UUID patientId,
            @Valid @RequestBody EhrUploadRequest request) {

        Sex sex;
        try {
            sex = Sex.valueOf(request.sex().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("sex", "INVALID_VALUE",
                    "sex must be one of: MALE, FEMALE, OTHER");
        }

        EhrRecord record = new EhrRecord(UUID.randomUUID(), PatientId.of(patientId),
                request.dateOfBirth(), sex, request.bmi(),
                request.diabetesOnsetDate(), request.hba1c(), request.fastingGlucose(),
                List.of(), List.of());

        ingestEhrUseCase.execute(record);

        return ResponseEntity.status(201).body(Map.of(
                "patientId", patientId.toString(),
                "status", "EHR_INGESTED"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN')")
    @Operation(summary = "Retrieve the EHR record for a patient",
               description = "Returns static EHR data (OBSERVED provenance). Returns 404 if no EHR exists for this patient.")
    public ResponseEntity<EhrResponse> get(@PathVariable UUID patientId) {
        EhrRecord record = getEhrUseCase.execute(PatientId.of(patientId));
        return ResponseEntity.ok(EhrResponse.from(record));
    }
}
