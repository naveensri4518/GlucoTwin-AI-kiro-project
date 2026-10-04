package com.glucotwin.infrastructure.persistence.repository;

import com.glucotwin.infrastructure.persistence.entity.SimulationResultJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SimulationResultJpaRepository
        extends JpaRepository<SimulationResultJpaEntity, UUID> {}
