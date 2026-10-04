package com.glucotwin.application;

import com.glucotwin.domain.patient.Patient;
import com.glucotwin.domain.patient.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListPatientsUseCase {

    private final PatientRepository patientRepository;

    @Transactional(readOnly = true)
    public Page<Patient> execute(Pageable pageable) {
        return patientRepository.findAll(pageable);
    }
}
