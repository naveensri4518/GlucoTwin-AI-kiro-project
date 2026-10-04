package com.glucotwin.api;

import com.glucotwin.api.dto.WearableEventRequest;
import com.glucotwin.application.IngestWearableEventUseCase;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.shared.ValidationException;
import com.glucotwin.domain.twin.ActivityLevel;
import com.glucotwin.domain.twin.SleepStage;
import com.glucotwin.domain.wearable.WearableEvent;
import com.glucotwin.infrastructure.config.GlucoTwinProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients/{patientId}/wearable-events")
@RequiredArgsConstructor
@Tag(name = "Wearable Events")
public class WearableEventController {

    private final IngestWearableEventUseCase ingestWearableEventUseCase;
    private final GlucoTwinProperties properties;

    @PostMapping
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN')")
    @Operation(summary = "Ingest a wearable sensor event")
    public ResponseEntity<Map<String, Object>> ingest(
            @PathVariable UUID patientId,
            @Valid @RequestBody WearableEventRequest request) {

        // Validate timestamp — not in the future, not > 24h old
        Instant now = Instant.now();
        if (request.eventTimestamp().isAfter(now)) {
            throw new ValidationException("eventTimestamp", "FUTURE_TIMESTAMP",
                    "eventTimestamp must not be in the future");
        }

        // Build domain event with clinical-warning check
        double glucose = request.glucoseReading();
        double clinicalWarningThreshold = properties.getData().getClinicalWarningThresholdMmol();

        SleepStage sleepStage = null;
        if (request.sleepStage() != null) {
            try { sleepStage = SleepStage.valueOf(request.sleepStage().toUpperCase()); }
            catch (IllegalArgumentException e) {
                throw new ValidationException("sleepStage", "INVALID_VALUE",
                        "sleepStage must be one of: AWAKE, LIGHT, DEEP, REM");
            }
        }

        ActivityLevel activityLevel = null;
        if (request.activityLevel() != null) {
            try { activityLevel = ActivityLevel.valueOf(request.activityLevel().toUpperCase()); }
            catch (IllegalArgumentException e) {
                throw new ValidationException("activityLevel", "INVALID_VALUE",
                        "activityLevel must be one of: SEDENTARY, LIGHT, MODERATE, VIGOROUS");
            }
        }

        UUID eventId = UUID.randomUUID();
        WearableEvent event = new WearableEvent(eventId, PatientId.of(patientId),
                glucose, request.heartRate(), request.hrv(), request.sleepDuration(),
                sleepStage, request.stepCount(), activityLevel,
                request.eventTimestamp(), null);

        ingestWearableEventUseCase.execute(event);

        Map<String, Object> body = Map.of(
                "eventId", eventId.toString(),
                "status", "QUEUED");
        return ResponseEntity.accepted().body(body);
    }
}
