package com.glucotwin.infrastructure.persistence.repository;

import com.glucotwin.infrastructure.persistence.entity.PredictionAuditLogJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PredictionAuditLogJpaRepository extends JpaRepository<PredictionAuditLogJpaEntity, Long> {}
