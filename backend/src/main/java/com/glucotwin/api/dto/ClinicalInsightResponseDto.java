package com.glucotwin.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.glucotwin.domain.insight.AgentExecutionTrace;
import com.glucotwin.domain.insight.ClinicalInsightResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST response DTO for POST /api/v1/patients/{patientId}/clinical-insights.
 *
 * Provenance rules:
 * - keyObservedSignals        → each carries provenance=OBSERVED
 * - spikeProbability etc.     → dataProvenance=OBSERVED+PREDICTED
 * - clinicalKnowledgeEvidence → each carries provenance=CLINICAL_KNOWLEDGE (Phase 9)
 * - safetyDisclaimer          → always present
 *
 * Phase 12: executionTrace — optional observability, @JsonInclude(NON_NULL).
 * Phase 13: explanation — optional LLM text, @JsonInclude(NON_NULL).
 *   Explanation never overrides deterministic clinical values.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClinicalInsightResponseDto(
        UUID patientId,
        Instant generatedAt,
        int twinStateVersion,
        UUID latestPredictionId,
        String riskCategory,
        double spikeProbability,
        ConfidenceIntervalDto confidenceInterval,
        List<ObservedSignalDto> keyObservedSignals,
        List<ContributingFactorDto> contributingFactors,
        List<String> dataQualityWarnings,
        String evidenceSummary,
        String uncertainty,
        String dataProvenance,
        String safetyDisclaimer,
        /** Phase 9: general clinical knowledge evidence. */
        List<ClinicalKnowledgeEvidenceDto> clinicalKnowledgeEvidence,
        /** Phase 12: pipeline execution trace. Omitted from JSON when null. */
        AgentExecutionTraceDto executionTrace,
        /**
         * Phase 13: LLM-generated explanation paragraph.
         * Omitted from JSON when null (LLM disabled, timed out, or failed).
         * Never contains numeric clinical values — those are in spikeProbability/riskCategory.
         */
        String explanation) {

    // ── Nested DTOs ───────────────────────────────────────────────────────────

    public record ConfidenceIntervalDto(double low, double high) {}

    public record ObservedSignalDto(String name, String value, String unit, String provenance) {}

    public record ContributingFactorDto(
            String factorName, double contribution, String direction) {}

    public record ClinicalKnowledgeEvidenceDto(
            String knowledgeId, String title, String sourceName, String sourceReference,
            String version, String topic, String excerpt, String provenance) {}

    /** Phase 12: pipeline execution trace DTO. */
    public record AgentExecutionTraceDto(
            String traceId, Instant startedAt, long totalDurationMs,
            List<AgentStepTraceDto> steps) {}

    /** Phase 12: single pipeline step trace DTO. */
    public record AgentStepTraceDto(
            String agentName, String status, long durationMs, String detail) {}

    // ── Factory ───────────────────────────────────────────────────────────────

    public static ClinicalInsightResponseDto from(ClinicalInsightResponse domain) {

        ConfidenceIntervalDto ci = new ConfidenceIntervalDto(
                domain.confidenceInterval().low(), domain.confidenceInterval().high());

        List<ObservedSignalDto> signals = domain.keyObservedSignals().stream()
                .map(s -> new ObservedSignalDto(s.name(), s.value(), s.unit(), s.provenance()))
                .toList();

        List<ContributingFactorDto> factors = domain.contributingFactors().stream()
                .map(f -> new ContributingFactorDto(
                        f.factorName(), f.contribution(), f.direction().name()))
                .toList();

        List<ClinicalKnowledgeEvidenceDto> knowledge = domain.clinicalKnowledgeEvidence().stream()
                .map(k -> new ClinicalKnowledgeEvidenceDto(
                        k.knowledgeId(), k.title(), k.sourceName(), k.sourceReference(),
                        k.version(), k.topic(), k.excerpt(), k.provenance()))
                .toList();

        AgentExecutionTraceDto traceDto = mapTrace(domain.executionTrace());

        return new ClinicalInsightResponseDto(
                domain.patientId(), domain.generatedAt(), domain.twinStateVersion(),
                domain.latestPredictionId(), domain.riskCategory().name(),
                domain.spikeProbability(), ci, signals, factors,
                domain.dataQualityWarnings(), domain.evidenceSummary(),
                domain.uncertainty(), domain.dataProvenance(), domain.safetyDisclaimer(),
                knowledge, traceDto,
                domain.explanation()); // Phase 13 — null when unavailable, omitted from JSON
    }

    private static AgentExecutionTraceDto mapTrace(AgentExecutionTrace trace) {
        if (trace == null) return null;
        List<AgentStepTraceDto> steps = trace.steps().stream()
                .map(s -> new AgentStepTraceDto(s.agentName(), s.status(), s.durationMs(), s.detail()))
                .toList();
        return new AgentExecutionTraceDto(
                trace.traceId(), trace.startedAt(), trace.totalDurationMs(), steps);
    }
}
