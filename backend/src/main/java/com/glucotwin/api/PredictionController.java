package com.glucotwin.api;

import com.glucotwin.api.dto.PredictionResponse;
import com.glucotwin.application.GetPredictionUseCase;
import com.glucotwin.application.ListPredictionsUseCase;
import com.glucotwin.application.TriggerPredictionUseCase;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.shared.PredictionId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Predictions")
public class PredictionController {

    private final TriggerPredictionUseCase triggerPredictionUseCase;
    private final GetPredictionUseCase getPredictionUseCase;
    private final ListPredictionsUseCase listPredictionsUseCase;

    @PostMapping("/api/v1/patients/{patientId}/predictions")
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN')")
    @Operation(summary = "Trigger an on-demand glucose spike prediction",
               description = "spikeProbability is a probabilistic estimate, not a certainty. " +
                             "For research and decision support only. Not a diagnostic system.")
    public ResponseEntity<Map<String, Object>> trigger(@PathVariable UUID patientId) {
        PredictionId predictionId = triggerPredictionUseCase.execute(PatientId.of(patientId), "MANUAL");

        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/../../../predictions/{id}")
                .buildAndExpand(predictionId.value()).toUri().normalize();

        return ResponseEntity.accepted().body(Map.of(
                "predictionId", predictionId.value().toString(),
                "status", "PENDING",
                "location", location.toString()));
    }

    @GetMapping("/api/v1/predictions/{predictionId}")
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN')")
    @Operation(summary = "Get a single prediction by ID")
    public ResponseEntity<PredictionResponse> get(@PathVariable UUID predictionId) {
        var record = getPredictionUseCase.execute(PredictionId.of(predictionId));
        return ResponseEntity.ok(PredictionResponse.from(record));
    }

    @GetMapping("/api/v1/patients/{patientId}/predictions")
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN')")
    @Operation(summary = "List predictions for a patient (paginated)")
    public ResponseEntity<Page<PredictionResponse>> list(
            @PathVariable UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {

        int clampedSize = Math.min(size, 100);
        var pageable = PageRequest.of(page, clampedSize);
        Page<PredictionResponse> result = listPredictionsUseCase
                .execute(PatientId.of(patientId), from, to, pageable)
                .map(PredictionResponse::from);
        return ResponseEntity.ok(result);
    }
}
