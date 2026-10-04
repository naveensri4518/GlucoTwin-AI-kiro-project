package com.glucotwin.infrastructure.messaging;

import com.glucotwin.domain.twin.TwinStateEventPublisher;
import com.glucotwin.domain.twin.TwinStateSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * No-op implementation of TwinStateEventPublisher for v1.
 * Will be replaced by SSE push when the doctor dashboard is implemented.
 */
@Component
@Slf4j
public class NoOpTwinStateEventPublisher implements TwinStateEventPublisher {

    @Override
    public void publishUpdated(TwinStateSnapshot snapshot) {
        log.debug("Twin state updated for patient {} version {} (SSE push not yet implemented)",
                snapshot.patientId(), snapshot.twinVersion());
    }
}
