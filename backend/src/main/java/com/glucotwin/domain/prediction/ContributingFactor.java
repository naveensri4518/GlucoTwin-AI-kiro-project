package com.glucotwin.domain.prediction;

import java.util.Objects;

/** A single SHAP-derived feature contribution to a prediction. */
public record ContributingFactor(
        String factorName,
        double contribution,
        RiskDirection direction) {

    public ContributingFactor {
        Objects.requireNonNull(factorName, "factorName must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        if (factorName.isBlank()) throw new IllegalArgumentException("factorName must not be blank");
    }
}
