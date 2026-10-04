package com.glucotwin.domain.insight;

import java.util.Objects;

/**
 * Result returned by a single specialized agent.
 * Either a success payload or an explicit failure — never silently empty.
 */
public sealed interface AgentResult<T> permits AgentResult.Success, AgentResult.Failure {

    record Success<T>(T value) implements AgentResult<T> {
        public Success {
            Objects.requireNonNull(value, "AgentResult.Success value must not be null");
        }
    }

    record Failure<T>(String agentName, String reason) implements AgentResult<T> {
        public Failure {
            Objects.requireNonNull(agentName, "agentName must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }

    static <T> AgentResult<T> success(T value) { return new Success<>(value); }
    static <T> AgentResult<T> failure(String agentName, String reason) {
        return new Failure<>(agentName, reason);
    }

    default boolean isSuccess() { return this instanceof Success; }

    default T valueOrThrow() {
        return switch (this) {
            case Success<T> s -> s.value();
            case Failure<T> f -> throw InsightGenerationException.agentFailure(f.agentName(), f.reason());
        };
    }
}
