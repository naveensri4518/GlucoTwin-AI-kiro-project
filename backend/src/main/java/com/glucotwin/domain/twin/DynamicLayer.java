package com.glucotwin.domain.twin;

import com.glucotwin.domain.shared.DataProvenance;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The wearable-derived dynamic layer of a Digital Twin.
 * Provenance is always OBSERVED.
 * Updated on every wearable event.
 */
public record DynamicLayer(
        Double glucoseReading,
        Double heartRate,
        Double hrv,
        Double sleepDuration,
        SleepStage sleepStage,
        Integer stepCount,
        ActivityLevel activityLevel,
        Instant eventTimestamp,
        List<CgmReading> cgmHistory,
        Set<String> dataQualityWarnings,
        DataProvenance dataProvenance) {

    /** Maximum number of CGM readings to retain in history buffer. */
    public static final int MAX_CGM_HISTORY = 12;

    public static DynamicLayer empty() {
        return new DynamicLayer(null, null, null, null, null, null,
                null, null, List.of(), Set.of(), DataProvenance.OBSERVED);
    }

    public DynamicLayer {
        if (dataProvenance != null && dataProvenance != DataProvenance.OBSERVED) {
            throw new IllegalArgumentException("DynamicLayer dataProvenance must be OBSERVED");
        }
        cgmHistory = cgmHistory == null ? List.of() : List.copyOf(cgmHistory);
        dataQualityWarnings = dataQualityWarnings == null ? Set.of() : Set.copyOf(dataQualityWarnings);
    }

    /**
     * Apply a new wearable event, returning an updated DynamicLayer.
     * The CGM history buffer is maintained as a rolling window capped at MAX_CGM_HISTORY.
     */
    public DynamicLayer applyEvent(
            Double newGlucose,
            Double newHeartRate,
            Double newHrv,
            Double newSleepDuration,
            SleepStage newSleepStage,
            Integer newStepCount,
            ActivityLevel newActivityLevel,
            Instant newTimestamp,
            Set<String> qualityWarnings) {

        // Update CGM history: add new reading, evict oldest if over limit
        List<CgmReading> updatedHistory = new ArrayList<>(cgmHistory);
        if (newGlucose != null) {
            updatedHistory.add(new CgmReading(newGlucose, newTimestamp));
            if (updatedHistory.size() > MAX_CGM_HISTORY) {
                updatedHistory.remove(0); // evict oldest
            }
        }
        // Sort by timestamp ascending for feature engineering
        updatedHistory.sort((a, b) -> a.timestamp().compareTo(b.timestamp()));

        Set<String> warnings = new HashSet<>(qualityWarnings == null ? Set.of() : qualityWarnings);

        return new DynamicLayer(
                newGlucose != null ? newGlucose : glucoseReading,
                newHeartRate != null ? newHeartRate : heartRate,
                newHrv != null ? newHrv : hrv,
                newSleepDuration != null ? newSleepDuration : sleepDuration,
                newSleepStage != null ? newSleepStage : sleepStage,
                newStepCount != null ? newStepCount : stepCount,
                newActivityLevel != null ? newActivityLevel : activityLevel,
                newTimestamp,
                Collections.unmodifiableList(updatedHistory),
                Collections.unmodifiableSet(warnings),
                DataProvenance.OBSERVED);
    }
}
