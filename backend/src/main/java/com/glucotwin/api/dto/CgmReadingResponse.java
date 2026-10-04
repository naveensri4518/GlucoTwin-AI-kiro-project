package com.glucotwin.api.dto;

import com.glucotwin.domain.twin.CgmReading;

import java.time.Instant;

/**
 * Response DTO for a single CGM reading in the history buffer.
 * All returned readings are OBSERVED provenance (actual wearable sensor data).
 * This is not a prediction. Do not infer diagnoses from these values.
 */
public record CgmReadingResponse(
        double value,
        Instant timestamp,
        String dataProvenance) {

    /** Always OBSERVED — CGM history comes from the wearable sensor, not the model. */
    public static CgmReadingResponse from(CgmReading reading) {
        return new CgmReadingResponse(
                reading.value(),
                reading.timestamp(),
                "OBSERVED");
    }
}
