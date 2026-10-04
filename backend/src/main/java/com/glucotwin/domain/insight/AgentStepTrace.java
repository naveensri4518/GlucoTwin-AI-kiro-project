package com.glucotwin.domain.insight;

import java.util.Objects;

/**
 * Immutable record of a single pipeline stage's execution result.
 *
 * <p>This is observability metadata only — never treated as clinical evidence.
 * No patient PII is stored here.
 */
public record AgentStepTrace(
        String agentName,
        String status,
        long durationMs,
        String detail) {

    /** Valid status values. */
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILURE = "FAILURE";
    public static final String STATUS_SKIPPED = "SKIPPED";

    private static final java.util.Set<String> VALID_STATUSES =
            java.util.Set.of(STATUS_SUCCESS, STATUS_FAILURE, STATUS_SKIPPED);

    public AgentStepTrace {
        Objects.requireNonNull(agentName, "agentName must not be null");
        Objects.requireNonNull(status,    "status must not be null");
        if (agentName.isBlank()) {
            throw new IllegalArgumentException("agentName must not be blank");
        }
        if (!VALID_STATUSES.contains(status)) {
            throw new IllegalArgumentException(
                    "status must be one of SUCCESS|FAILURE|SKIPPED, got: " + status);
        }
        if (durationMs < 0) {
            throw new IllegalArgumentException(
                    "durationMs must be >= 0, got: " + durationMs);
        }
        // detail may be empty but must not be null
        if (detail == null) {
            detail = "";
        }
    }

    // ── Factory helpers ───────────────────────────────────────────────────────

    public static AgentStepTrace success(String agentName, long durationMs, String detail) {
        return new AgentStepTrace(agentName, STATUS_SUCCESS, durationMs, detail);
    }

    public static AgentStepTrace failure(String agentName, long durationMs, String detail) {
        return new AgentStepTrace(agentName, STATUS_FAILURE, durationMs, detail);
    }

    public static AgentStepTrace skipped(String agentName) {
        return new AgentStepTrace(agentName, STATUS_SKIPPED, 0L, "Stage not reached");
    }
}
