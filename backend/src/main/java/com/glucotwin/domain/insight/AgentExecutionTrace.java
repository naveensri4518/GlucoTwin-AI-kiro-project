package com.glucotwin.domain.insight;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable record of the full clinical insight pipeline execution.
 *
 * <p>This is observability metadata only — never treated as clinical evidence.
 * The {@code traceId} comes from MDC and may be null if no trace context is active.
 * No patient PII is stored here.
 */
public record AgentExecutionTrace(
        String traceId,
        Instant startedAt,
        long totalDurationMs,
        List<AgentStepTrace> steps) {

    public AgentExecutionTrace {
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        if (totalDurationMs < 0) {
            throw new IllegalArgumentException(
                    "totalDurationMs must be >= 0, got: " + totalDurationMs);
        }
        // null step list becomes empty immutable list; list content is defensively copied
        steps = steps != null ? List.copyOf(steps) : List.of();
    }
}
