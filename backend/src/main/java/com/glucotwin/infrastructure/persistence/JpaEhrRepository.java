package com.glucotwin.infrastructure.persistence;

import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.twin.EhrRecord;
import com.glucotwin.domain.twin.EhrRepository;
import com.glucotwin.domain.twin.*;
import com.glucotwin.infrastructure.persistence.entity.PatientEhrJpaEntity;
import com.glucotwin.infrastructure.persistence.repository.PatientEhrJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
@RequiredArgsConstructor
public class JpaEhrRepository implements EhrRepository {

    private final PatientEhrJpaRepository jpaRepo;

    @Override
    public EhrRecord save(EhrRecord record) {
        var existing = jpaRepo.findByPatientId(record.patientId().value());
        PatientEhrJpaEntity entity = existing.orElse(new PatientEhrJpaEntity());
        entity.setPatientId(record.patientId().value());
        entity.setDateOfBirth(record.dateOfBirth());
        entity.setSex(record.sex().name());
        entity.setBmi(record.bmi());
        entity.setDiabetesOnsetDate(record.diabetesOnsetDate());
        entity.setHba1c(record.hba1c());
        entity.setFastingGlucose(record.fastingGlucose());
        entity.setDataSource("SYNTHETIC");
        // Medications and lab results as list-of-maps
        List<Map<String, Object>> meds = new ArrayList<>();
        for (Medication m : record.medications()) {
            meds.add(Map.of("name", m.name(), "dose", m.dose(), "frequency", m.frequency()));
        }
        entity.setMedications(meds);
        jpaRepo.save(entity);
        return record;
    }

    @Override
    public Optional<EhrRecord> findByPatientId(PatientId patientId) {
        return jpaRepo.findByPatientId(patientId.value())
                .map(e -> new EhrRecord(
                        e.getEhrId() != null ? e.getEhrId() : UUID.randomUUID(),
                        PatientId.of(e.getPatientId()),
                        e.getDateOfBirth(),
                        Sex.valueOf(e.getSex()),
                        e.getBmi(),
                        e.getDiabetesOnsetDate(),
                        e.getHba1c(),
                        e.getFastingGlucose(),
                        List.of(),
                        List.of()));
    }
}
