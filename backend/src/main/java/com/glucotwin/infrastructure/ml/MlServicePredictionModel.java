package com.glucotwin.infrastructure.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.glucotwin.domain.prediction.*;
import com.glucotwin.domain.twin.TwinStateSnapshot;
import com.glucotwin.infrastructure.config.GlucoTwinProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Calls the Python ML service to produce predictions.
 * Implements PredictionModelPort.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MlServicePredictionModel implements PredictionModelPort {

    private final GlucoTwinProperties properties;
    private final ObjectMapper objectMapper;
    private RestClient restClient;
    private volatile String cachedModelVersion = "unknown";

    @PostConstruct
    void init() {
        restClient = RestClient.builder()
                .baseUrl(properties.getMlService().getUrl())
                .defaultHeader("X-Internal-Token", properties.getMlService().getInternalToken())
                .build();
        // Try to fetch model version at startup
        try {
            var health = restClient.get().uri("/health")
                    .retrieve().body(Map.class);
            if (health != null && health.containsKey("modelVersion")) {
                cachedModelVersion = (String) health.get("modelVersion");
            }
        } catch (Exception e) {
            log.warn("Could not fetch model version from ML service at startup: {}", e.getMessage());
        }
    }

    @Override
    public PredictionResult predict(TwinStateSnapshot snapshot) {
        try {
            MlPredictionRequest request = MlServiceMapper.toRequest(snapshot);

            MlPredictionResponse response = restClient.post()
                    .uri("/predict")
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.is5xxServerError(), (req, res) -> {
                        throw new PredictionServiceUnavailableException(
                                "ML service returned " + res.getStatusCode());
                    })
                    .onStatus(status -> status.value() == 422, (req, res) -> {
                        throw new GlucoseReadingRequiredException(
                                snapshot.patientId().toString());
                    })
                    .body(MlPredictionResponse.class);

            if (response == null) {
                throw new PredictionServiceUnavailableException("ML service returned empty response");
            }

            cachedModelVersion = response.modelVersion();
            return MlServiceMapper.toResult(response);

        } catch (ResourceAccessException e) {
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                throw new PredictionTimeoutException("ML service timed out", e);
            }
            throw new PredictionServiceUnavailableException("ML service unreachable: " + e.getMessage(), e);
        } catch (PredictionServiceUnavailableException | PredictionTimeoutException
                 | GlucoseReadingRequiredException ex) {
            throw ex;
        } catch (Exception e) {
            throw new PredictionServiceUnavailableException("Unexpected ML service error: " + e.getMessage(), e);
        }
    }

    @Override
    public String getModelVersion() {
        return cachedModelVersion;
    }
}
