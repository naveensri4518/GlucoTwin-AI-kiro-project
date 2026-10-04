package com.glucotwin.infrastructure.persistence.repository;

import com.glucotwin.infrastructure.persistence.entity.DeadLetterEventJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetterEventJpaRepository extends JpaRepository<DeadLetterEventJpaEntity, Long> {
    Page<DeadLetterEventJpaEntity> findByResolvedFalseOrderByCreatedAtDesc(Pageable pageable);
}
