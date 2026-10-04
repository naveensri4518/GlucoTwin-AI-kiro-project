package com.glucotwin.domain.insight;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Phase 12 — AgentStepTrace and AgentExecutionTrace unit tests.
 */
class AgentExecutionTraceTest {

    // ── AgentStepTrace ────────────────────────────────────────────────────────

    @Test
    void stepTrace_success_constructsCorrectly() {
        AgentStepTrace step = AgentStepTrace.success("Twin Analysis", 12L, "OK");
        assertThat(step.agentName()).isEqualTo("Twin Analysis");
        assertThat(step.status()).isEqualTo(AgentStepTrace.STATUS_SUCCESS);
        assertThat(step.durationMs()).isEqualTo(12L);
        assertThat(step.detail()).isEqualTo("OK");
    }

    @Test
    void stepTrace_failure_constructsCorrectly() {
        AgentStepTrace step = AgentStepTrace.failure("Prediction Analysis", 5L, "No prediction found");
        assertThat(step.status()).isEqualTo(AgentStepTrace.STATUS_FAILURE);
        assertThat(step.detail()).isEqualTo("No prediction found");
    }

    @Test
    void stepTrace_skipped_constructsCorrectly() {
        AgentStepTrace step = AgentStepTrace.skipped("Risk Evidence");
        assertThat(step.status()).isEqualTo(AgentStepTrace.STATUS_SKIPPED);
        assertThat(step.durationMs()).isEqualTo(0L);
        assertThat(step.detail()).isEqualTo("Stage not reached");
    }

    @Test
    void stepTrace_nullAgentName_throws() {
        assertThatThrownBy(() ->
                new AgentStepTrace(null, AgentStepTrace.STATUS_SUCCESS, 0L, "detail"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void stepTrace_blankAgentName_throws() {
        assertThatThrownBy(() ->
                new AgentStepTrace("  ", AgentStepTrace.STATUS_SUCCESS, 0L, "detail"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentName");
    }

    @Test
    void stepTrace_invalidStatus_throws() {
        assertThatThrownBy(() ->
                new AgentStepTrace("Agent", "RUNNING", 0L, "detail"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SUCCESS|FAILURE|SKIPPED");
    }

    @Test
    void stepTrace_negativeDuration_throws() {
        assertThatThrownBy(() ->
                new AgentStepTrace("Agent", AgentStepTrace.STATUS_SUCCESS, -1L, "detail"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("durationMs");
    }

    @Test
    void stepTrace_nullDetail_normalizedToEmpty() {
        AgentStepTrace step = new AgentStepTrace("Agent", AgentStepTrace.STATUS_SUCCESS, 0L, null);
        assertThat(step.detail()).isEqualTo("");
    }

    @Test
    void stepTrace_zeroDuration_isValid() {
        assertThatCode(() ->
                AgentStepTrace.success("Verification", 0L, "instant"))
                .doesNotThrowAnyException();
    }

    // ── AgentExecutionTrace ───────────────────────────────────────────────────

    @Test
    void executionTrace_immutableConstruction() {
        List<AgentStepTrace> steps = new ArrayList<>();
        steps.add(AgentStepTrace.success("Twin Analysis", 10L, "ok"));

        AgentExecutionTrace trace = new AgentExecutionTrace(
                "trace-abc", Instant.now(), 100L, steps);

        // Mutating the source list must not affect the trace
        steps.add(AgentStepTrace.skipped("Extra Stage"));
        assertThat(trace.steps()).hasSize(1);
    }

    @Test
    void executionTrace_nullStepsBecomeEmptyList() {
        AgentExecutionTrace trace = new AgentExecutionTrace(
                null, Instant.now(), 0L, null);
        assertThat(trace.steps()).isEmpty();
        assertThat(trace.steps()).isNotNull();
    }

    @Test
    void executionTrace_stepsCannotBeMutated() {
        AgentExecutionTrace trace = new AgentExecutionTrace(
                "tid", Instant.now(), 50L,
                List.of(AgentStepTrace.success("A", 5L, "ok")));

        assertThatThrownBy(() ->
                trace.steps().add(AgentStepTrace.skipped("B")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void executionTrace_negativeTotalDuration_throws() {
        assertThatThrownBy(() ->
                new AgentExecutionTrace("tid", Instant.now(), -1L, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalDurationMs");
    }

    @Test
    void executionTrace_nullStartedAt_throws() {
        assertThatThrownBy(() ->
                new AgentExecutionTrace("tid", null, 0L, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void executionTrace_traceIdMayBeNull() {
        assertThatCode(() ->
                new AgentExecutionTrace(null, Instant.now(), 0L, List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void executionTrace_zeroDurationIsValid() {
        assertThatCode(() ->
                new AgentExecutionTrace("tid", Instant.now(), 0L, List.of()))
                .doesNotThrowAnyException();
    }
}
