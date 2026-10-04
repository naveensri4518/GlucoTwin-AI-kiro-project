package com.glucotwin.api.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record WearableEventRequest(
        @NotNull(message = "glucoseReading is required")
        @DecimalMin(value = "1.0", message = "glucoseReading must be >= 1.0 mmol/L")
        @DecimalMax(value = "35.0", message = "glucoseReading must be <= 35.0 mmol/L")
        Double glucoseReading,

        @DecimalMin(value = "20.0", message = "heartRate must be >= 20 bpm")
        @DecimalMax(value = "300.0", message = "heartRate must be <= 300 bpm")
        Double heartRate,

        @DecimalMin(value = "0.0", message = "hrv must be >= 0 ms")
        @DecimalMax(value = "300.0", message = "hrv must be <= 300 ms")
        Double hrv,

        @DecimalMin(value = "0.0", message = "sleepDuration must be >= 0 hours")
        @DecimalMax(value = "24.0", message = "sleepDuration must be <= 24 hours")
        Double sleepDuration,

        String sleepStage,

        @Min(value = 0, message = "stepCount must be >= 0")
        @Max(value = 100000, message = "stepCount must be <= 100,000")
        Integer stepCount,

        String activityLevel,

        @NotNull(message = "eventTimestamp is required")
        Instant eventTimestamp) {}
