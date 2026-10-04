package com.glucotwin.infrastructure.persistence.mapper;

import com.glucotwin.domain.shared.DataProvenance;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.twin.*;
import com.glucotwin.infrastructure.persistence.entity.DigitalTwinStateJpaEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bidirectional mapper between DigitalTwinState domain object and JPA entity. */
public final class DigitalTwinStateMapper {

    private DigitalTwinStateMapper() {}

    public static DigitalTwinStateJpaEntity toEntity(DigitalTwinState domain) {
        var entity = new DigitalTwinStateJpaEntity();
        entity.setPatientId(domain.getPatientId().value());
        entity.setTwinVersion(domain.getTwinVersion());
        entity.setStatus(domain.getStatus().name());
        entity.setLastUpdatedAt(domain.getLastUpdatedAt());
        entity.setCreatedAt(domain.getCreatedAt());

        DynamicLayer dyn = domain.getDynamicLayer();
        if (dyn != null) {
            entity.setGlucoseReading(dyn.glucoseReading());
            entity.setHeartRate(dyn.heartRate());
            entity.setHrv(dyn.hrv());
            entity.setSleepDuration(dyn.sleepDuration());
            entity.setSleepStage(dyn.sleepStage() != null ? dyn.sleepStage().name() : null);
            entity.setStepCount(dyn.stepCount());
            entity.setActivityLevel(dyn.activityLevel() != null ? dyn.activityLevel().name() : null);
            entity.setWearableEventAt(dyn.eventTimestamp());
            // Map CGM history
            List<DigitalTwinStateJpaEntity.CgmReadingJson> cgmJson = new ArrayList<>();
            for (CgmReading r : dyn.cgmHistory()) {
                cgmJson.add(new DigitalTwinStateJpaEntity.CgmReadingJson(r.value(), r.timestamp()));
            }
            entity.setCgmHistory(cgmJson);
            // Data quality flags
            entity.setDataQualityFlags(dyn.dataQualityWarnings().toArray(new String[0]));
        }
        return entity;
    }

    public static DigitalTwinState toDomain(DigitalTwinStateJpaEntity entity) {
        PatientId patientId = PatientId.of(entity.getPatientId());

        StaticLayer staticLayer = StaticLayer.empty();

        // Rebuild dynamic layer from entity fields
        List<CgmReading> cgmHistory = new ArrayList<>();
        if (entity.getCgmHistory() != null) {
            for (var r : entity.getCgmHistory()) {
                cgmHistory.add(new CgmReading(r.value(), r.timestamp()));
            }
        }
        Set<String> warnings = new HashSet<>();
        if (entity.getDataQualityFlags() != null) {
            for (String f : entity.getDataQualityFlags()) warnings.add(f);
        }

        DynamicLayer dynamicLayer = new DynamicLayer(
                entity.getGlucoseReading(),
                entity.getHeartRate(),
                entity.getHrv(),
                entity.getSleepDuration(),
                entity.getSleepStage() != null ? SleepStage.valueOf(entity.getSleepStage()) : null,
                entity.getStepCount(),
                entity.getActivityLevel() != null ? ActivityLevel.valueOf(entity.getActivityLevel()) : null,
                entity.getWearableEventAt(),
                cgmHistory,
                warnings,
                DataProvenance.OBSERVED);

        TwinStatus status = TwinStatus.valueOf(entity.getStatus());

        return new DigitalTwinState(patientId, status, entity.getTwinVersion(),
                staticLayer, dynamicLayer, entity.getLastUpdatedAt(), entity.getCreatedAt());
    }
}
