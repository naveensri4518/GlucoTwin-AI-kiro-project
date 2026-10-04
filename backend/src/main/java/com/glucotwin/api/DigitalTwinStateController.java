package com.glucotwin.api;

import com.glucotwin.application.GetDigitalTwinStateUseCase;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.twin.DigitalTwinState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients/{patientId}/twin-state")
@RequiredArgsConstructor
@Tag(name = "Digital Twin")
public class DigitalTwinStateController {

    private final GetDigitalTwinStateUseCase getDigitalTwinStateUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN')")
    @Operation(summary = "Get the current Digital Twin state for a patient")
    public ResponseEntity<Map<String, Object>> get(@PathVariable UUID patientId) {
        DigitalTwinState twin = getDigitalTwinStateUseCase.execute(PatientId.of(patientId));

        Map<String, Object> response = new HashMap<>();
        response.put("patientId", patientId.toString());
        response.put("twinVersion", twin.getTwinVersion());
        response.put("status", twin.getStatus().name());
        response.put("lastUpdatedAt", twin.getLastUpdatedAt());
        response.put("createdAt", twin.getCreatedAt());

        if (twin.getDynamicLayer() != null) {
            var dl = twin.getDynamicLayer();
            Map<String, Object> dynamic = new HashMap<>();
            dynamic.put("glucoseReading", dl.glucoseReading());
            dynamic.put("heartRate", dl.heartRate());
            dynamic.put("hrv", dl.hrv());
            dynamic.put("sleepDuration", dl.sleepDuration());
            dynamic.put("sleepStage", dl.sleepStage() != null ? dl.sleepStage().name() : null);
            dynamic.put("stepCount", dl.stepCount());
            dynamic.put("activityLevel", dl.activityLevel() != null ? dl.activityLevel().name() : null);
            dynamic.put("eventTimestamp", dl.eventTimestamp());
            dynamic.put("dataProvenance", "OBSERVED");
            dynamic.put("cgmHistorySize", dl.cgmHistory().size());
            dynamic.put("dataQualityWarnings", dl.dataQualityWarnings());
            response.put("dynamicLayer", dynamic);
        }

        return ResponseEntity.ok(response);
    }
}
