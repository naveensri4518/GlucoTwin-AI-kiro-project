package com.glucotwin.infrastructure.messaging;

import com.glucotwin.application.TriggerPredictionUseCase;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.infrastructure.persistence.repository.DeadLetterEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Redis stream consumer that triggers predictions on wearable events.
 * Retry with exponential backoff is handled by the configuration (see RedisStreamConfig).
 * After max retries, events are dead-lettered.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WearableEventConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final TriggerPredictionUseCase triggerPredictionUseCase;
    private final DeadLetterEventPersister deadLetterPersister;

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        Map<String, String> fields = message.getValue();
        String patientIdStr = fields.get("patientId");
        String eventIdStr = fields.get("eventId");

        log.info("WEARABLE_EVENT_CONSUMED eventId={} stream={}", eventIdStr, message.getStream());

        if (patientIdStr == null) {
            log.error("Received wearable event message without patientId: {}", message.getId());
            return;
        }

        try {
            PatientId patientId = PatientId.of(UUID.fromString(patientIdStr));
            triggerPredictionUseCase.execute(patientId, "AUTO");
        } catch (Exception e) {
            log.error("Failed to process wearable event message eventId={}: {}",
                    eventIdStr, e.getMessage());
            // Dead-letter
            try {
                Map<String, Object> payload = new HashMap<>(fields);
                UUID eventId = eventIdStr != null ? UUID.fromString(eventIdStr) : UUID.randomUUID();
                UUID patientId = patientIdStr != null ? UUID.fromString(patientIdStr) : null;
                deadLetterPersister.persist(eventId, patientId, payload, e.getMessage(), 0);
            } catch (Exception dlEx) {
                log.error("Failed to persist dead-letter event: {}", dlEx.getMessage());
            }
        }
    }
}
