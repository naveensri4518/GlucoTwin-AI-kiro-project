package com.glucotwin.application;

import com.glucotwin.domain.patient.Patient;
import com.glucotwin.domain.patient.PatientRepository;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.shared.ResourceNotFoundException;
import com.glucotwin.domain.twin.DigitalTwinRepository;
import com.glucotwin.domain.twin.DigitalTwinState;
import com.glucotwin.domain.wearable.WearableEvent;
import com.glucotwin.domain.wearable.WearableEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validates and ingests a wearable event, updates the Digital Twin,
 * and publishes the event to the Redis stream to trigger a prediction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestWearableEventUseCase {

    private final PatientRepository patientRepository;
    private final DigitalTwinRepository digitalTwinRepository;
    private final WearableEventPublisher wearableEventPublisher;

    @Transactional
    public void execute(WearableEvent event) {
        PatientId patientId = event.patientId();

        // Assert patient exists
        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient", patientId.toString());
        }

        log.info("WEARABLE_EVENT_RECEIVED patientId={} eventId={}", patientId, event.eventId());

        // Apply event to digital twin
        DigitalTwinState twin = digitalTwinRepository.findByPatientId(patientId)
                .orElse(DigitalTwinState.create(patientId));

        twin.applyWearableEvent(event);
        digitalTwinRepository.save(twin);

        log.info("TWIN_STATE_UPDATED patientId={} twinVersion={} status={}",
                patientId, twin.getTwinVersion(), twin.getStatus());

        // Publish to Redis stream — triggers prediction via consumer
        wearableEventPublisher.publish(event);
    }
}
