package com.glucotwin.application;

import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.shared.ResourceNotFoundException;
import com.glucotwin.domain.twin.EhrRecord;
import com.glucotwin.domain.twin.EhrRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetEhrUseCase {

    private final EhrRepository ehrRepository;

    @Transactional(readOnly = true)
    public EhrRecord execute(PatientId patientId) {
        return ehrRepository.findByPatientId(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("EhrRecord", patientId.toString()));
    }
}
