package com.glucotwin.infrastructure.persistence;

import com.glucotwin.domain.prediction.PredictionRecord;
import com.glucotwin.domain.prediction.PredictionRepository;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.shared.PredictionId;
import com.glucotwin.infrastructure.persistence.mapper.PredictionRecordMapper;
import com.glucotwin.infrastructure.persistence.repository.GlucosePredictionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JpaPredictionRepository implements PredictionRepository {

    private final GlucosePredictionJpaRepository jpaRepo;

    @Override
    public PredictionRecord save(PredictionRecord record) {
        var entity = PredictionRecordMapper.toEntity(record);
        var saved = jpaRepo.save(entity);
        return PredictionRecordMapper.toDomain(saved);
    }

    @Override
    public Optional<PredictionRecord> findById(PredictionId id) {
        return jpaRepo.findById(id.value()).map(PredictionRecordMapper::toDomain);
    }

    @Override
    public Page<PredictionRecord> findByPatientId(PatientId patientId, Pageable pageable) {
        return jpaRepo.findByPatientIdOrderByPredictedAtDesc(patientId.value(), pageable)
                .map(PredictionRecordMapper::toDomain);
    }

    @Override
    public Page<PredictionRecord> findByPatientIdAndTimeRange(
            PatientId patientId, Instant from, Instant to, Pageable pageable) {
        return jpaRepo.findByPatientIdAndTimeRange(patientId.value(), from, to, pageable)
                .map(PredictionRecordMapper::toDomain);
    }

    @Override
    public Page<PredictionRecord> findByModelVersion(String modelVersion, Pageable pageable) {
        return jpaRepo.findByModelVersionOrderByPredictedAtDesc(modelVersion, pageable)
                .map(PredictionRecordMapper::toDomain);
    }
}
