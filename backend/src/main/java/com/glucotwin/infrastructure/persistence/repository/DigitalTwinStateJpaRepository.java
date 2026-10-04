package com.glucotwin.infrastructure.persistence.repository;

import com.glucotwin.infrastructure.persistence.entity.DigitalTwinStateJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DigitalTwinStateJpaRepository extends JpaRepository<DigitalTwinStateJpaEntity, UUID> {

    @Query("SELECT t FROM DigitalTwinStateJpaEntity t WHERE t.status != 'ARCHIVED' " +
           "AND (t.wearableEventAt IS NULL OR t.wearableEventAt < :threshold)")
    List<DigitalTwinStateJpaEntity> findStaleAfter(@Param("threshold") Instant threshold);
}
