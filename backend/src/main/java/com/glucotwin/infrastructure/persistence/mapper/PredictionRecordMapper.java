package com.glucotwin.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.glucotwin.domain.prediction.*;
import com.glucotwin.domain.shared.DataProvenance;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.shared.PredictionId;
import com.glucotwin.infrastructure.persistence.entity.GlucosePredictionJpaEntity;

import java.util.*;

public final class PredictionRecordMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PredictionRecordMapper() {}

    public static GlucosePredictionJpaEntity toEntity(PredictionRecord domain) {
        var entity = new GlucosePredictionJpaEntity();
        entity.setPredictionId(domain.getPredictionId().value());
        entity.setPatientId(domain.getPatientId().value());
        entity.setStatus(domain.getStatus().name());
        entity.setPredictedAt(domain.getPredictedAt());
        entity.setPredictionHorizonHrs(domain.getPredictionHorizonHours());
        entity.setSpikeProbability(domain.getSpikeProbability());
        entity.setRiskCategory(domain.getRiskCategory() != null ? domain.getRiskCategory().name() : null);

        if (domain.getConfidenceInterval() != null) {
            entity.setCiLow(domain.getConfidenceInterval().low());
            entity.setCiHigh(domain.getConfidenceInterval().high());
        }

        // Contributing factors as JSON
        List<Map<String, Object>> factors = new ArrayList<>();
        for (ContributingFactor cf : domain.getTopContributingFactors()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("factorName", cf.factorName());
            m.put("contribution", cf.contribution());
            m.put("direction", cf.direction().name());
            factors.add(m);
        }
        entity.setContributingFactors(factors);

        entity.setDataProvenance(domain.getDataProvenance().name());
        entity.setTwinStateVersion(domain.getTwinStateVersion());
        entity.setModelVersion(domain.getModelVersion());
        entity.setDataQualityWarnings(domain.getDataQualityWarnings().toArray(new String[0]));
        entity.setFailureReason(domain.getFailureReason());
        entity.setTriggeredBy(domain.getTriggeredBy());

        // Feature vector as flat JSON map
        if (domain.getFeatureVector() != null) {
            entity.setFeatureVector(featureVectorToMap(domain.getFeatureVector()));
        }

        return entity;
    }

    public static PredictionRecord toDomain(GlucosePredictionJpaEntity entity) {
        PredictionId predictionId = PredictionId.of(entity.getPredictionId());
        PatientId patientId = PatientId.of(entity.getPatientId());
        PredictionStatus status = PredictionStatus.valueOf(entity.getStatus());

        ConfidenceInterval ci = null;
        if (entity.getCiLow() != null && entity.getCiHigh() != null) {
            ci = new ConfidenceInterval(entity.getCiLow(), entity.getCiHigh());
        }

        List<ContributingFactor> factors = new ArrayList<>();
        if (entity.getContributingFactors() != null) {
            for (Map<String, Object> m : entity.getContributingFactors()) {
                factors.add(new ContributingFactor(
                        (String) m.get("factorName"),
                        ((Number) m.get("contribution")).doubleValue(),
                        RiskDirection.valueOf((String) m.get("direction"))));
            }
        }

        List<String> warnings = entity.getDataQualityWarnings() != null
                ? Arrays.asList(entity.getDataQualityWarnings()) : List.of();

        return new PredictionRecord(
                predictionId, patientId, status,
                entity.getPredictedAt(), entity.getPredictionHorizonHrs(),
                entity.getSpikeProbability(),
                entity.getRiskCategory() != null ? RiskCategory.valueOf(entity.getRiskCategory()) : null,
                ci, factors,
                DataProvenance.PREDICTED,
                entity.getTwinStateVersion(), entity.getModelVersion(),
                warnings, entity.getFailureReason(), null,
                entity.getTriggeredBy());
    }

    private static Map<String, Object> featureVectorToMap(FeatureVector fv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cgmCurrent", fv.cgmCurrent());
        m.put("cgmDelta30m", fv.cgmDelta30m());
        m.put("cgmMean60m", fv.cgmMean60m());
        m.put("cgmSlope60m", fv.cgmSlope60m());
        m.put("heartRateCurrent", fv.heartRateCurrent());
        m.put("hrvCurrent", fv.hrvCurrent());
        m.put("sleepDurationLast", fv.sleepDurationLast());
        m.put("stepCountToday", fv.stepCountToday());
        m.put("activityLevelEncoded", fv.activityLevelEncoded());
        m.put("hba1cLatest", fv.hba1cLatest());
        m.put("bmi", fv.bmi());
        m.put("timeOfDaySin", fv.timeOfDaySin());
        m.put("timeOfDayCos", fv.timeOfDayCos());
        m.put("daysSinceDiagnosis", fv.daysSinceDiagnosis());
        m.put("imputedFields", fv.imputedFields());
        return m;
    }
}
