package com.glucotwin.domain.prediction;

/**
 * Configurable risk category thresholds.
 * Boundary values fall into the upper category (≥ threshold → upper).
 * Default: LOW < 0.30, MODERATE [0.30, 0.60), HIGH [0.60, 0.85), CRITICAL ≥ 0.85
 */
public record RiskThresholds(double lowMax, double moderateMax, double highMax) {

    public static final RiskThresholds DEFAULT = new RiskThresholds(0.30, 0.60, 0.85);

    public RiskThresholds {
        if (lowMax <= 0.0 || lowMax >= 1.0)
            throw new IllegalArgumentException("lowMax must be in (0, 1): " + lowMax);
        if (moderateMax <= lowMax || moderateMax >= 1.0)
            throw new IllegalArgumentException("moderateMax must be > lowMax and < 1: " + moderateMax);
        if (highMax <= moderateMax || highMax >= 1.0)
            throw new IllegalArgumentException("highMax must be > moderateMax and < 1: " + highMax);
    }
}
