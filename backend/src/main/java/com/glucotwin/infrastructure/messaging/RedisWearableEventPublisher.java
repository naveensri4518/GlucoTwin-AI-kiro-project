package com.glucotwin.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.glucotwin.domain.wearable.WearableEvent;
import com.glucotwin.domain.wearable.WearableEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisWearableEventPublisher implements WearableEventPublisher {

    private static final String STREAM_KEY_PREFIX = "glucotwin:wearable-events:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(WearableEvent event) {
        String streamKey = STREAM_KEY_PREFIX + event.patientId().value();
        String traceId = MDC.get("traceId");

        try {
            Map<String, String> fields = new HashMap<>();
            fields.put("eventId", event.eventId().toString());
            fields.put("patientId", event.patientId().value().toString());
            fields.put("glucoseReading", String.valueOf(event.glucoseReading()));
            if (event.heartRate() != null) fields.put("heartRate", String.valueOf(event.heartRate()));
            if (event.hrv() != null) fields.put("hrv", String.valueOf(event.hrv()));
            if (event.sleepDuration() != null) fields.put("sleepDuration", String.valueOf(event.sleepDuration()));
            if (event.sleepStage() != null) fields.put("sleepStage", event.sleepStage().name());
            if (event.stepCount() != null) fields.put("stepCount", String.valueOf(event.stepCount()));
            if (event.activityLevel() != null) fields.put("activityLevel", event.activityLevel().name());
            fields.put("eventTimestamp", event.eventTimestamp().toString());
            if (traceId != null) fields.put("traceId", traceId);

            redisTemplate.opsForStream().add(streamKey, fields);
            log.debug("Published wearable event {} to Redis stream {}", event.eventId(), streamKey);
        } catch (Exception e) {
            log.error("Failed to publish wearable event {} to Redis: {}", event.eventId(), e.getMessage());
            throw new RuntimeException("Failed to publish wearable event to Redis stream", e);
        }
    }
}
