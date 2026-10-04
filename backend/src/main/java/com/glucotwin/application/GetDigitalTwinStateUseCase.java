package com.glucotwin.application;

import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.shared.ResourceNotFoundException;
import com.glucotwin.domain.twin.DigitalTwinRepository;
import com.glucotwin.domain.twin.DigitalTwinState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetDigitalTwinStateUseCase {

    private final DigitalTwinRepository digitalTwinRepository;

    @Transactional(readOnly = true)
    public DigitalTwinState execute(PatientId patientId) {
        return digitalTwinRepository.findByPatientId(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("DigitalTwinState", patientId.toString()));
    }
}
