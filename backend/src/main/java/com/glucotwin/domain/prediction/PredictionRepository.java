package com.glucotwin.domain.prediction;

import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.shared.PredictionId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Optional;

/** Domain port for prediction record persistence. Implemented by infrastructure layer. */
public interface PredictionRepository {

    PredictionRecord save(PredictionRecord record);

    Optional<PredictionRecord> findById(PredictionId id);

    Page<PredictionRecord> findByPatientId(PatientId patientId, Pageable pageable);

    Page<PredictionRecord> findByPatientIdAndTimeRange(
            PatientId patientId, Instant from, Instant to, Pageable pageable);

    Page<PredictionRecord> findByModelVersion(String modelVersion, Pageable pageable);
}
