package com.glucotwin.domain.prediction;

/**
 * 95% confidence interval for a spike probability.
 * Invariant: low <= high, both in [0.0, 1.0], width > 0.
 */
public record ConfidenceInterval(double low, double high) {

    public ConfidenceInterval {
        if (low < 0.0 || low > 1.0) {
            throw new IllegalArgumentException("CI low must be in [0.0, 1.0], got: " + low);
        }
        if (high < 0.0 || high > 1.0) {
            throw new IllegalArgumentException("CI high must be in [0.0, 1.0], got: " + high);
        }
        if (low > high) {
            throw new IllegalArgumentException(
                    "CI low (" + low + ") must be <= high (" + high + ")");
        }
        if (Double.compare(low, high) == 0) {
            throw new IllegalArgumentException(
                    "CI width must be > 0; degenerate interval is a model defect");
        }
    }

    /** Width of the confidence interval. Always > 0. */
    public double width() {
        return high - low;
    }
}
