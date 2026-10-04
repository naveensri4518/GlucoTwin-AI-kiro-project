package com.glucotwin.domain.twin;

import com.glucotwin.domain.shared.PatientId;

import java.util.Optional;

public interface EhrRepository {
    EhrRecord save(EhrRecord record);
    Optional<EhrRecord> findByPatientId(PatientId patientId);
}
