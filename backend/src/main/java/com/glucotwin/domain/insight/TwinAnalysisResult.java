package com.glucotwin.domain.insight;

import com.glucotwin.domain.twin.TwinStatus;

import java.util.List;
import java.util.Objects;

/**
 * Output of the TwinAnalysisAgent.
 * Contains only OBSERVED signals extracted from the Digital Twin's dynamic and static layers.
 * Never contains PREDICTED or SIMULATED data.
 */
public record TwinAnalysisResult(
        int twinStateVersion,
        TwinStatus twinStatus,
        List<ObservedSignal> keyObservedSignals,
        List<String> dataQualityWarnings,
        boolean hasDynamicLayer,
        boolean hasStaticLayer) {

    public TwinAnalysisResult {
        Objects.requireNonNull(twinStatus);
        keyObservedSignals  = keyObservedSignals  != null ? List.copyOf(keyObservedSignals)  : List.of();
        dataQualityWarnings = dataQualityWarnings != null ? List.copyOf(dataQualityWarnings) : List.of();
    }
}
