package com.glucotwin.application;

import com.glucotwin.domain.prediction.PredictionRecord;
import com.glucotwin.domain.prediction.PredictionRepository;
import com.glucotwin.domain.shared.PatientId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ListPredictionsUseCase {

    private final PredictionRepository predictionRepository;

    @Transactional(readOnly = true)
    public Page<PredictionRecord> execute(PatientId patientId, Instant from, Instant to, Pageable pageable) {
        if (from != null && to != null) {
            return predictionRepository.findByPatientIdAndTimeRange(patientId, from, to, pageable);
        }
        return predictionRepository.findByPatientId(patientId, pageable);
    }
}
