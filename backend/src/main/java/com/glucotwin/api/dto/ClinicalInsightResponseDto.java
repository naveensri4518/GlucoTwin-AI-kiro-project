package com.glucotwin.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.glucotwin.domain.insight.ClinicalInsightResponse;
import com.glucotwin.domain.insight.ObservedSignal;
import com.glucotwin.domain.prediction.ContributingFactor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST response DTO for POST /api/v1/patients/{patientId}/clinical-insights.
 *
 * Provenance rules mirrored from the domain:
 * - keyObservedSignals  → each carries provenance=OBSERVED
 * - spikeProbability / riskCategory / confidenceInterval → dataProvenance=OBSERVED+PREDICTED
 * - safetyDisclaimer is always present
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
        String safetyDisclaimer) {

    // ── Nested DTOs ───────────────────────────────────────────────────────────

    public record ConfidenceIntervalDto(double low, double high) {}

    public record ObservedSignalDto(String name, String value, String unit, String provenance) {}

    public record ContributingFactorDto(
            String factorName, double contribution, String direction) {}

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
                domain.safetyDisclaimer());
    }
}
