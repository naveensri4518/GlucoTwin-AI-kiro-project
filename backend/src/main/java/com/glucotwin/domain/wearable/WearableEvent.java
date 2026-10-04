package com.glucotwin.domain.wearable;

import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.twin.ActivityLevel;
import com.glucotwin.domain.twin.SleepStage;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain event representing a single wearable sensor reading.
 * All data is OBSERVED provenance.
 */
public record WearableEvent(
        UUID eventId,
        PatientId patientId,
        double glucoseReading,
        Double heartRate,
        Double hrv,
        Double sleepDuration,
        SleepStage sleepStage,
        Integer stepCount,
        ActivityLevel activityLevel,
        Instant eventTimestamp,
        String traceId) {

    public WearableEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(patientId, "patientId must not be null");
        Objects.requireNonNull(eventTimestamp, "eventTimestamp must not be null");
    }
}
