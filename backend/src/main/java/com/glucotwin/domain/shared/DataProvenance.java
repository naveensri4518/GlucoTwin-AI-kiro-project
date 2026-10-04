package com.glucotwin.domain.shared;

/**
 * Labels every data item by its origin.
 * OBSERVED  = actual measured/recorded data from EHR or wearable
 * PREDICTED = model-generated probabilistic output
 * SIMULATED = hypothetical what-if scenario data (reserved for future)
 */
public enum DataProvenance {
    OBSERVED,
    PREDICTED,
    SIMULATED
}
