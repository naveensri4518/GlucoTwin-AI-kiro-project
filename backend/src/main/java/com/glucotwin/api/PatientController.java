package com.glucotwin.api;

import com.glucotwin.api.dto.PatientSummaryResponse;
import com.glucotwin.application.ListPatientsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
@Tag(name = "Patients")
public class PatientController {

    private final ListPatientsUseCase listPatientsUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN')")
    @Operation(
            summary = "List all registered patients (paginated)",
            description = "Returns a paginated list of patients for dashboard navigation. " +
                          "Ordered by registration date descending. " +
                          "Fetch /api/v1/patients/{id}/ehr for clinical detail.")
    public ResponseEntity<Page<PatientSummaryResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int clampedSize = Math.min(size, 100);
        var pageable = PageRequest.of(page, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PatientSummaryResponse> result = listPatientsUseCase.execute(pageable)
                .map(PatientSummaryResponse::from);
        return ResponseEntity.ok(result);
    }
}
