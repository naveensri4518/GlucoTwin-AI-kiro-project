package com.glucotwin.application;

import com.glucotwin.domain.patient.Patient;
import com.glucotwin.domain.patient.PatientRepository;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.twin.DigitalTwinRepository;
import com.glucotwin.domain.twin.DigitalTwinState;
import com.glucotwin.domain.twin.EhrRecord;
import com.glucotwin.domain.twin.EhrRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ingest or replace EHR data for a patient.
 * Does NOT trigger prediction — prediction requires wearable context (REQ-004).
 * Creates the patient record if it doesn't exist (upsert, per spec assumption A2).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestEhrUseCase {

    private final PatientRepository patientRepository;
    private final EhrRepository ehrRepository;
    private final DigitalTwinRepository digitalTwinRepository;

    @Transactional
    public void execute(EhrRecord ehrRecord) {
        PatientId patientId = ehrRecord.patientId();

        // Upsert patient (create if first EHR upload)
        if (!patientRepository.existsById(patientId)) {
            patientRepository.save(Patient.create(patientId));
            log.info("PATIENT_CREATED patientId={}", patientId);
        }

        // Persist EHR record
        ehrRepository.save(ehrRecord);

        // Apply to digital twin static layer (create twin if needed)
        DigitalTwinState twin = digitalTwinRepository.findByPatientId(patientId)
                .orElse(DigitalTwinState.create(patientId));

        twin.applyEhrRecord(ehrRecord);
        digitalTwinRepository.save(twin);

        log.info("EHR_INGESTED patientId={} twinVersion={}", patientId, twin.getTwinVersion());
    }
}
