package com.glucotwin.domain.prediction;

import java.util.Set;

/**
 * Immutable feature vector produced by the ML service and stored for audit.
 * Nulls on optional features indicate they were imputed from population medians.
 */
public record FeatureVector(
        double cgmCurrent,
        Double cgmDelta30m,
        Double cgmMean60m,
        Double cgmSlope60m,
        Double heartRateCurrent,
        Double hrvCurrent,
        Double sleepDurationLast,
        Integer stepCountToday,
        int activityLevelEncoded,
        Double hba1cLatest,
        Double bmi,
        double timeOfDaySin,
        double timeOfDayCos,
        long daysSinceDiagnosis,
        Set<String> imputedFields) {

    public FeatureVector {
        imputedFields = imputedFields == null ? Set.of() : Set.copyOf(imputedFields);
    }
}
