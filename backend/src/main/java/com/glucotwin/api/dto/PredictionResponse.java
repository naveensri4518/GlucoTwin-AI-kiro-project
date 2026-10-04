package com.glucotwin.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.glucotwin.domain.prediction.*;
import com.glucotwin.domain.shared.DataProvenance;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full prediction response returned by GET /api/v1/predictions/{predictionId} */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PredictionResponse(
        UUID predictionId,
        UUID patientId,
        Instant predictedAt,
        int predictionHorizonHours,
        String status,
        Double spikeProbability,
        String riskCategory,
        ConfidenceIntervalDto confidenceInterval,
        List<ContributingFactorDto> topContributingFactors,
        String dataProvenance,
        int twinStateVersion,
        String modelVersion,
        List<String> dataQualityWarnings,
        String failureReason) {

    public record ConfidenceIntervalDto(double low, double high) {}

    public record ContributingFactorDto(String factorName, double contribution, String direction) {}

    public static PredictionResponse from(PredictionRecord record) {
        ConfidenceIntervalDto ci = record.getConfidenceInterval() != null
                ? new ConfidenceIntervalDto(
                        record.getConfidenceInterval().low(),
                        record.getConfidenceInterval().high())
                : null;

        List<ContributingFactorDto> factors = record.getTopContributingFactors().stream()
                .map(f -> new ContributingFactorDto(f.factorName(), f.contribution(), f.direction().name()))
                .toList();

        return new PredictionResponse(
                record.getPredictionId().value(),
                record.getPatientId().value(),
                record.getPredictedAt(),
                record.getPredictionHorizonHours(),
                record.getStatus().name(),
                record.getSpikeProbability(),
                record.getRiskCategory() != null ? record.getRiskCategory().name() : null,
                ci,
                factors,
                record.getDataProvenance() != null ? record.getDataProvenance().name() : DataProvenance.PREDICTED.name(),
                record.getTwinStateVersion(),
                record.getModelVersion(),
                record.getDataQualityWarnings(),
                record.getFailureReason());
    }
}
