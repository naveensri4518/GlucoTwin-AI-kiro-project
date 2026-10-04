package com.glucotwin.infrastructure.persistence.repository;

import com.glucotwin.infrastructure.persistence.entity.GlucosePredictionJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface GlucosePredictionJpaRepository extends JpaRepository<GlucosePredictionJpaEntity, UUID> {

    Page<GlucosePredictionJpaEntity> findByPatientIdOrderByPredictedAtDesc(UUID patientId, Pageable pageable);

    @Query("SELECT p FROM GlucosePredictionJpaEntity p WHERE p.patientId = :patientId " +
           "AND p.predictedAt BETWEEN :from AND :to ORDER BY p.predictedAt DESC")
    Page<GlucosePredictionJpaEntity> findByPatientIdAndTimeRange(
            @Param("patientId") UUID patientId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    Page<GlucosePredictionJpaEntity> findByModelVersionOrderByPredictedAtDesc(
            String modelVersion, Pageable pageable);
}
