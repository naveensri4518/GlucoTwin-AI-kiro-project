package com.glucotwin.domain.prediction;

/**
 * Derives a RiskCategory from a spike probability using configurable thresholds.
 * Boundary rule: value exactly at a threshold falls into the upper category.
 * e.g. 0.30 → MODERATE (not LOW), 0.60 → HIGH (not MODERATE).
 */
public final class RiskCategoryDeriver {

    private RiskCategoryDeriver() {}

    public static RiskCategory derive(double probability, RiskThresholds thresholds) {
        if (probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("probability must be in [0.0, 1.0]: " + probability);
        }
        // Boundary: >= threshold falls into upper category
        if (probability >= thresholds.highMax()) return RiskCategory.CRITICAL;
        if (probability >= thresholds.moderateMax()) return RiskCategory.HIGH;
        if (probability >= thresholds.lowMax()) return RiskCategory.MODERATE;
        return RiskCategory.LOW;
    }
}
