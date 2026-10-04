package com.glucotwin.application;

import com.glucotwin.domain.prediction.PredictionRecord;
import com.glucotwin.domain.prediction.PredictionRepository;
import com.glucotwin.domain.shared.PredictionId;
import com.glucotwin.domain.shared.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetPredictionUseCase {

    private final PredictionRepository predictionRepository;

    @Transactional(readOnly = true)
    public PredictionRecord execute(PredictionId predictionId) {
        return predictionRepository.findById(predictionId)
                .orElseThrow(() -> new ResourceNotFoundException("PredictionRecord", predictionId.toString()));
    }
}
