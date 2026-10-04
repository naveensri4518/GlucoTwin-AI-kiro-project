package com.glucotwin.application;

import com.glucotwin.domain.twin.DigitalTwinRepository;
import com.glucotwin.domain.twin.DigitalTwinState;
import com.glucotwin.infrastructure.config.GlucoTwinProperties;
import com.glucotwin.infrastructure.observability.PatientIdRef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarkStaleTwinsUseCase {

    private final DigitalTwinRepository digitalTwinRepository;
    private final GlucoTwinProperties properties;

    @Transactional
    public int execute() {
        Duration threshold = Duration.ofMinutes(properties.getTwin().getStalenessThresholdMinutes());
        List<DigitalTwinState> staleTwins = digitalTwinRepository.findStaleAfter(threshold);

        for (DigitalTwinState twin : staleTwins) {
            twin.markStale();
            digitalTwinRepository.save(twin);
            log.warn("TWIN_STATE_STALE patientId={}", PatientIdRef.hash(twin.getPatientId()));
        }

        if (!staleTwins.isEmpty()) {
            log.info("Stale twin detection: marked {} twins as STALE", staleTwins.size());
        }

        return staleTwins.size();
    }
}
