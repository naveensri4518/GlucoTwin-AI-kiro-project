package com.glucotwin.domain.patient;

import com.glucotwin.domain.shared.PatientId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface PatientRepository {
    Patient save(Patient patient);
    Optional<Patient> findById(PatientId patientId);
    boolean existsById(PatientId patientId);
    Page<Patient> findAll(Pageable pageable);
}
