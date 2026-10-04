package com.glucotwin.domain.insight;

import com.glucotwin.domain.shared.GlucoTwinException;

/**
 * Thrown when the clinical insight orchestration cannot produce a result
 * due to insufficient data, agent failure, or a safety constraint violation.
 *
 * Never fabricate an insight when required evidence is unavailable — throw this instead.
 */
public class InsightGenerationException extends GlucoTwinException {

    private final String errorCode;

    public InsightGenerationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    // ── Factory helpers for known failure modes ───────────────────────────────

    public static InsightGenerationException insufficientData(String detail) {
        return new InsightGenerationException("INSIGHT_INSUFFICIENT_DATA",
                "Insufficient data to generate insight: " + detail);
    }

    public static InsightGenerationException agentFailure(String agentName, String detail) {
        return new InsightGenerationException("INSIGHT_AGENT_FAILURE",
                "Agent '" + agentName + "' failed: " + detail);
    }

    public static InsightGenerationException twinUnavailable(String patientId) {
        return new InsightGenerationException("INSIGHT_TWIN_UNAVAILABLE",
                "Digital Twin unavailable for patient: " + patientId);
    }

    public static InsightGenerationException predictionUnavailable(String patientId) {
        return new InsightGenerationException("INSIGHT_PREDICTION_UNAVAILABLE",
                "No completed prediction available for patient: " + patientId
                + ". Generate a prediction first.");
    }
}
