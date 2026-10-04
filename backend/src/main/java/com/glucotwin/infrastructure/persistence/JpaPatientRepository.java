package com.glucotwin.infrastructure.persistence;

import com.glucotwin.domain.patient.Patient;
import com.glucotwin.domain.patient.PatientRepository;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.infrastructure.persistence.entity.PatientJpaEntity;
import com.glucotwin.infrastructure.persistence.repository.PatientJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JpaPatientRepository implements PatientRepository {

    private final PatientJpaRepository jpaRepo;

    @Override
    public Patient save(Patient patient) {
        var entity = new PatientJpaEntity();
        entity.setPatientId(patient.getPatientId().value());
        entity.setCreatedAt(patient.getCreatedAt());
        entity.setUpdatedAt(patient.getUpdatedAt());
        jpaRepo.save(entity);
        return patient;
    }

    @Override
    public Optional<Patient> findById(PatientId patientId) {
        return jpaRepo.findById(patientId.value())
                .map(e -> new Patient(PatientId.of(e.getPatientId()), e.getCreatedAt()));
    }

    @Override
    public boolean existsById(PatientId patientId) {
        return jpaRepo.existsById(patientId.value());
    }
}
