package com.glucotwin.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.glucotwin.domain.insight.ClinicalInsightResponse;
import com.glucotwin.domain.insight.ClinicalKnowledgeEvidence;

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
 * Phase 9: clinicalKnowledgeEvidence is an additive field.
 * Existing API consumers that do not read it are unaffected.
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
        /** Phase 9: general clinical knowledge evidence for the identified risk factors. */
        List<ClinicalKnowledgeEvidenceDto> clinicalKnowledgeEvidence) {

    // ── Nested DTOs ───────────────────────────────────────────────────────────

    public record ConfidenceIntervalDto(double low, double high) {}

    public record ObservedSignalDto(String name, String value, String unit, String provenance) {}

    public record ContributingFactorDto(
            String factorName, double contribution, String direction) {}

    /**
     * Phase 9 DTO for a single clinical knowledge evidence item.
     * provenance is always "CLINICAL_KNOWLEDGE".
     */
    public record ClinicalKnowledgeEvidenceDto(
            String knowledgeId,
            String title,
            String sourceName,
            String sourceReference,
            String version,
            String topic,
            String excerpt,
            String provenance) {}

    // ── Factory ───────────────────────────────────────────────────────────────

    public static ClinicalInsightResponseDto from(ClinicalInsightResponse domain) {

        ConfidenceIntervalDto ci = new ConfidenceIntervalDto(
                domain.confidenceInterval().low(),
                domain.confidenceInterval().high());

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

        return new ClinicalInsightResponseDto(
                domain.patientId(),
                domain.generatedAt(),
                domain.twinStateVersion(),
                domain.latestPredictionId(),
                domain.riskCategory().name(),
                domain.spikeProbability(),
                ci,
                signals,
                factors,
                domain.dataQualityWarnings(),
                domain.evidenceSummary(),
                domain.uncertainty(),
                domain.dataProvenance(),
                domain.safetyDisclaimer(),
                knowledge);
    }
}
