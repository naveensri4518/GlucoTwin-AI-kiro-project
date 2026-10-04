package com.glucotwin.infrastructure.messaging;

import com.glucotwin.infrastructure.persistence.entity.DeadLetterEventJpaEntity;
import com.glucotwin.infrastructure.persistence.repository.DeadLetterEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeadLetterEventPersister {

    private final DeadLetterEventJpaRepository repo;

    public void persist(UUID eventId, UUID patientId, Map<String, Object> payload,
                        String failureReason, int retryCount) {
        var entity = new DeadLetterEventJpaEntity();
        entity.setEventId(eventId);
        entity.setPatientId(patientId);
        entity.setPayload(payload);
        entity.setFailureReason(failureReason);
        entity.setRetryCount(retryCount);
        repo.save(entity);
        log.error("DEAD_LETTER_EVENT eventId={} retryCount={} reason={}", eventId, retryCount, failureReason);
    }
}
