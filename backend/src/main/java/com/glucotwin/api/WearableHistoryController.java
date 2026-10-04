package com.glucotwin.api;

import com.glucotwin.api.dto.CgmReadingResponse;
import com.glucotwin.application.GetDigitalTwinStateUseCase;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.twin.DigitalTwinState;
import com.glucotwin.domain.twin.DynamicLayer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Exposes the existing rolling CGM history buffer from the Digital Twin.
 *
 * <p>All returned readings are OBSERVED provenance (wearable sensor data).
 * This endpoint is for clinical decision support only — values are not predictions
 * or medical diagnoses.
 */
@RestController
@RequestMapping("/api/v1/patients/{patientId}/wearable-events")
@RequiredArgsConstructor
@Tag(name = "Wearable Events")
public class WearableHistoryController {

    private final GetDigitalTwinStateUseCase getDigitalTwinStateUseCase;

    /**
     * Returns the rolling CGM history buffer (up to the configured max size, default 12 readings).
     * Readings are ordered oldest-first (ascending by timestamp) as maintained by the domain.
     *
     * <p>Returns HTTP 200 with an empty array if the patient exists but has no CGM history yet.
     * Returns HTTP 404 if the patient's Digital Twin does not exist.
     */
    @GetMapping("/cgm-history")
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN')")
    @Operation(
            summary = "Get rolling CGM glucose history for a patient",
            description = "Returns the most recent glucose readings from the Digital Twin's rolling buffer. " +
                          "All values are OBSERVED provenance (wearable sensor data, not predictions). " +
                          "For clinical decision support only — not a diagnostic endpoint.")
    public ResponseEntity<List<CgmReadingResponse>> getCgmHistory(@PathVariable UUID patientId) {
        DigitalTwinState twin = getDigitalTwinStateUseCase.execute(PatientId.of(patientId));

        DynamicLayer dl = twin.getDynamicLayer();
        if (dl == null || dl.cgmHistory().isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<CgmReadingResponse> readings = dl.cgmHistory().stream()
                .map(CgmReadingResponse::from)
                .toList();

        return ResponseEntity.ok(readings);
    }
}
