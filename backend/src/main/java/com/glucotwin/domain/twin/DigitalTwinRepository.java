package com.glucotwin.domain.twin;

import com.glucotwin.domain.shared.PatientId;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Domain port for Digital Twin state persistence. Implemented by infrastructure layer. */
public interface DigitalTwinRepository {

    Optional<DigitalTwinState> findByPatientId(PatientId patientId);

    /** Save a DigitalTwinState. Throws OptimisticLockRetryExhaustedException on version conflict. */
    DigitalTwinState save(DigitalTwinState state);

    /** Returns twins whose last wearable event was received more than threshold ago. */
    List<DigitalTwinState> findStaleAfter(Duration threshold);
}
