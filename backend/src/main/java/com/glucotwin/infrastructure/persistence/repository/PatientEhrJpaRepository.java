package com.glucotwin.infrastructure.persistence.repository;

import com.glucotwin.infrastructure.persistence.entity.PatientEhrJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PatientEhrJpaRepository extends JpaRepository<PatientEhrJpaEntity, UUID> {
    Optional<PatientEhrJpaEntity> findByPatientId(UUID patientId);
}
