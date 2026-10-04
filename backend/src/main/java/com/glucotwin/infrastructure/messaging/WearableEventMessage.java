package com.glucotwin.infrastructure.messaging;

import java.time.Instant;

/**
 * JSON-serialisable message for Redis stream.
 * Mirrors WearableEvent domain record.
 */
public record WearableEventMessage(
        String eventId,
        String patientId,
        double glucoseReading,
        Double heartRate,
        Double hrv,
        Double sleepDuration,
        String sleepStage,
        Integer stepCount,
        String activityLevel,
        Instant eventTimestamp,
        String traceId) {}
