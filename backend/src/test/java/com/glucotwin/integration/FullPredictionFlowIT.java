package com.glucotwin.integration;

import com.glucotwin.application.IngestEhrUseCase;
import com.glucotwin.application.IngestWearableEventUseCase;
import com.glucotwin.application.TriggerPredictionUseCase;
import com.glucotwin.domain.prediction.PredictionRecord;
import com.glucotwin.domain.prediction.PredictionRepository;
import com.glucotwin.domain.prediction.PredictionStatus;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.shared.PredictionId;
import com.glucotwin.domain.twin.*;
import com.glucotwin.domain.wearable.WearableEvent;
import com.glucotwin.test.mock.MockPredictionModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Full integration test: EHR upload -> wearable event -> prediction.
 * Uses Testcontainers PostgreSQL and a MockPredictionModel (no ML service needed).
 *
 * NOTE: These tests require Docker to be running.
 * Mark as @Tag("integration") — excluded from unit test runs with: gradle test -x :test
 */
@Tag("integration")
@SpringBootTest
@Testcontainers
class FullPredictionFlowIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("glucotwin_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Disable Redis for integration tests (use no-op publisher)
        registry.add("spring.data.redis.url", () -> "redis://localhost:6379");
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        com.glucotwin.domain.prediction.PredictionModelPort mockPredictionModel() {
            return new MockPredictionModel(0.75);
        }

        @Bean
        @Primary
        com.glucotwin.domain.wearable.WearableEventPublisher noOpWearablePublisher() {
            return event -> {}; // no-op for integration tests
        }
    }

    @Autowired
    private IngestEhrUseCase ingestEhrUseCase;

    @Autowired
    private TriggerPredictionUseCase triggerPredictionUseCase;

    @Autowired
    private PredictionRepository predictionRepository;

    @Autowired
    private DigitalTwinRepository digitalTwinRepository;

    @Test
    void fullFlow_ehrUpload_thenWearableEvent_thenPrediction() {
        PatientId patientId = PatientId.random();

        // 1. Upload EHR
        EhrRecord ehrRecord = new EhrRecord(UUID.randomUUID(), patientId,
                LocalDate.of(1975, 6, 15), Sex.MALE, 28.5,
                LocalDate.of(2015, 3, 1), 7.2, 6.1, List.of(), List.of());
        ingestEhrUseCase.execute(ehrRecord);

        // 2. Apply wearable event directly to twin (bypass Redis for this test)
        DigitalTwinState twin = digitalTwinRepository.findByPatientId(patientId)
                .orElseThrow(() -> new AssertionError("Twin not found after EHR upload"));

        WearableEvent event = new WearableEvent(UUID.randomUUID(), patientId,
                8.4, 72.0, 45.0, 6.5, SleepStage.LIGHT, 4200, ActivityLevel.LIGHT,
                Instant.now(), null);
        twin.applyWearableEvent(event);
        digitalTwinRepository.save(twin);

        // 3. Trigger prediction
        PredictionId predictionId = triggerPredictionUseCase.execute(patientId, "AUTO");
        assertThat(predictionId).isNotNull();

        // 4. Verify prediction record
        Optional<PredictionRecord> record = predictionRepository.findById(predictionId);
        assertThat(record).isPresent();
        assertThat(record.get().getStatus()).isEqualTo(PredictionStatus.COMPLETED);
        assertThat(record.get().getSpikeProbability()).isBetween(0.0, 1.0);
        assertThat(record.get().getRiskCategory()).isNotNull();
        assertThat(record.get().getConfidenceInterval()).isNotNull();
        assertThat(record.get().getDataProvenance())
                .isEqualTo(com.glucotwin.domain.shared.DataProvenance.PREDICTED);
        assertThat(record.get().getPredictionHorizonHours()).isEqualTo(2);
        assertThat(record.get().getModelVersion()).isNotBlank();
    }

    @Test
    void twinStateVersion_inPrediction_matchesSnapshotVersion() {
        PatientId patientId = PatientId.random();

        // Setup
        EhrRecord ehr = new EhrRecord(UUID.randomUUID(), patientId,
                LocalDate.of(1970, 1, 1), Sex.FEMALE, 25.0,
                LocalDate.of(2010, 1, 1), 6.8, 5.8, List.of(), List.of());
        ingestEhrUseCase.execute(ehr);

        DigitalTwinState twin = digitalTwinRepository.findByPatientId(patientId)
                .orElseThrow();
        WearableEvent event = new WearableEvent(UUID.randomUUID(), patientId,
                9.0, 80.0, 40.0, 7.0, SleepStage.AWAKE, 3000, ActivityLevel.SEDENTARY,
                Instant.now(), null);
        twin.applyWearableEvent(event);
        DigitalTwinState savedTwin = digitalTwinRepository.save(twin);
        int expectedVersion = savedTwin.getTwinVersion();

        // Trigger prediction
        PredictionId predictionId = triggerPredictionUseCase.execute(patientId, "MANUAL");
        PredictionRecord record = predictionRepository.findById(predictionId).orElseThrow();

        // twinStateVersion must match the version at snapshot time
        assertThat(record.getTwinStateVersion()).isEqualTo(expectedVersion);
    }

    @Test
    void staleTwin_predictionIncludesStalenessWarning() {
        PatientId patientId = PatientId.random();

        // Setup with EHR + wearable
        EhrRecord ehr = new EhrRecord(UUID.randomUUID(), patientId,
                LocalDate.of(1980, 5, 20), Sex.MALE, 30.0,
                LocalDate.of(2018, 6, 1), 8.1, 7.5, List.of(), List.of());
        ingestEhrUseCase.execute(ehr);

        DigitalTwinState twin = digitalTwinRepository.findByPatientId(patientId).orElseThrow();
        twin.applyWearableEvent(new WearableEvent(UUID.randomUUID(), patientId,
                10.0, 85.0, 35.0, 5.5, SleepStage.AWAKE, 1000, ActivityLevel.SEDENTARY,
                Instant.now(), null));
        twin.markStale(); // Force stale
        digitalTwinRepository.save(twin);

        // Trigger prediction
        PredictionId predictionId = triggerPredictionUseCase.execute(patientId, "AUTO");
        PredictionRecord record = predictionRepository.findById(predictionId).orElseThrow();

        assertThat(record.getDataQualityWarnings()).contains("STALE_WEARABLE_DATA");
    }
}
