package com.glucotwin.infrastructure.persistence;

import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.twin.DigitalTwinRepository;
import com.glucotwin.domain.twin.DigitalTwinState;
import com.glucotwin.infrastructure.persistence.mapper.DigitalTwinStateMapper;
import com.glucotwin.infrastructure.persistence.repository.DigitalTwinStateJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Slf4j
public class JpaDigitalTwinRepository implements DigitalTwinRepository {

    private final DigitalTwinStateJpaRepository jpaRepo;

    @Override
    public Optional<DigitalTwinState> findByPatientId(PatientId patientId) {
        return jpaRepo.findById(patientId.value())
                .map(DigitalTwinStateMapper::toDomain);
    }

    @Override
    public DigitalTwinState save(DigitalTwinState state) {
        var entity = DigitalTwinStateMapper.toEntity(state);
        try {
            var saved = jpaRepo.save(entity);
            return DigitalTwinStateMapper.toDomain(saved);
        } catch (OptimisticLockingFailureException ex) {
            // Retry once
            log.warn("Optimistic lock conflict for patient {}, retrying once", state.getPatientId());
            var saved = jpaRepo.save(entity);
            return DigitalTwinStateMapper.toDomain(saved);
        }
    }

    @Override
    public List<DigitalTwinState> findStaleAfter(Duration threshold) {
        Instant cutoff = Instant.now().minus(threshold);
        return jpaRepo.findStaleAfter(cutoff).stream()
                .map(DigitalTwinStateMapper::toDomain)
                .collect(Collectors.toList());
    }
}
