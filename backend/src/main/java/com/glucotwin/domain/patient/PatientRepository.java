package com.glucotwin.domain.patient;

import com.glucotwin.domain.shared.PatientId;

import java.util.Optional;

public interface PatientRepository {
    Patient save(Patient patient);
    Optional<Patient> findById(PatientId patientId);
    boolean existsById(PatientId patientId);
}
