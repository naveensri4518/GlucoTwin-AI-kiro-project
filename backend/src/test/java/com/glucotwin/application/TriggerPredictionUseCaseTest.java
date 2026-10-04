package com.glucotwin.application;

import com.glucotwin.domain.prediction.*;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.twin.*;
import com.glucotwin.infrastructure.config.GlucoTwinProperties;
import com.glucotwin.infrastructure.observability.AuditLogger;
import com.glucotwin.test.factory.TwinStateSnapshotFactory;
import com.glucotwin.test.mock.MockPredictionModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TriggerPredictionUseCaseTest {

    @Mock private DigitalTwinRepository digitalTwinRepository;
    @Mock private PredictionRepository predictionRepository;
    @Mock private TwinStateEventPublisher twinStateEventPublisher;
    @Mock private AuditLogger auditLogger;

    private MockPredictionModel mockModel;
    private TriggerPredictionUseCase useCase;

    @BeforeEach
    void setUp() {
        mockModel = new MockPredictionModel(0.75);
        GlucoTwinProperties props = new GlucoTwinProperties();
        useCase = new TriggerPredictionUseCase(
                digitalTwinRepository, predictionRepository, mockModel,
                twinStateEventPublisher, auditLogger, props);
    }

    @Test
    void execute_happyPath_returnsCompletedPredictionId() {
        DigitalTwinState twin = activeTwin();
        when(digitalTwinRepository.findByPatientId(any())).thenReturn(Optional.of(twin));
        when(predictionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PatientId patientId = twin.getPatientId();
        var predictionId = useCase.execute(patientId, "MANUAL");

        assertThat(predictionId).isNotNull();
        assertThat(mockModel.getCallCount()).isEqualTo(1);
    }

    @Test
    void execute_mlFailure_savesFailedRecord() {
        DigitalTwinState twin = activeTwin();
        when(digitalTwinRepository.findByPatientId(any())).thenReturn(Optional.of(twin));
        when(predictionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockModel.willThrow(new PredictionServiceUnavailableException("ML service down"));

        assertThatThrownBy(() -> useCase.execute(twin.getPatientId(), "AUTO"))
                .isInstanceOf(PredictionServiceUnavailableException.class);

        // Verify save was called twice: once for PENDING, once for FAILED
        verify(predictionRepository, times(2)).save(argThat(
                r -> r instanceof PredictionRecord));
    }

    @Test
    void execute_noGlucoseReading_throwsGlucoseReadingRequired() {
        DigitalTwinState twin = twinWithoutGlucose();
        when(digitalTwinRepository.findByPatientId(any())).thenReturn(Optional.of(twin));

        assertThatThrownBy(() -> useCase.execute(twin.getPatientId(), "AUTO"))
                .isInstanceOf(GlucoseReadingRequiredException.class);

        // ML model must NOT be called
        assertThat(mockModel.getCallCount()).isEqualTo(0);
    }

    @Test
    void execute_staleTwin_predictionIncludesStalenessWarning() {
        DigitalTwinState twin = staleTwin();
        when(digitalTwinRepository.findByPatientId(any())).thenReturn(Optional.of(twin));

        // Capture what gets saved
        when(predictionRepository.save(any())).thenAnswer(inv -> {
            PredictionRecord r = inv.getArgument(0);
            return r;
        });

        useCase.execute(twin.getPatientId(), "AUTO");

        verify(predictionRepository, atLeast(1)).save(argThat(r -> {
            PredictionRecord pr = (PredictionRecord) r;
            return pr.getStatus() == PredictionStatus.COMPLETED
                    && pr.getDataQualityWarnings().contains("STALE_WEARABLE_DATA");
        }));
    }

    @Test
    void execute_archivedTwin_throwsTwinStateArchivedError() {
        DigitalTwinState twin = archivedTwin();
        when(digitalTwinRepository.findByPatientId(any())).thenReturn(Optional.of(twin));

        assertThatThrownBy(() -> useCase.execute(twin.getPatientId(), "MANUAL"))
                .isInstanceOf(TwinStateArchivedError.class);
    }

    @Test
    void execute_completedRecord_hasCorrectRiskCategory() {
        DigitalTwinState twin = activeTwin();
        when(digitalTwinRepository.findByPatientId(any())).thenReturn(Optional.of(twin));
        when(predictionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockModel.withProbability(0.75); // -> HIGH (>= 0.60, < 0.85)
        useCase.execute(twin.getPatientId(), "AUTO");

        verify(predictionRepository, atLeast(1)).save(argThat(r -> {
            PredictionRecord pr = (PredictionRecord) r;
            return pr.getStatus() == PredictionStatus.COMPLETED
                    && pr.getRiskCategory() == RiskCategory.HIGH;
        }));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private DigitalTwinState activeTwin() {
        PatientId id = PatientId.random();
        DigitalTwinState twin = DigitalTwinState.create(id);
        twin.applyWearableEvent(new com.glucotwin.domain.wearable.WearableEvent(
                java.util.UUID.randomUUID(), id, 8.4, 72.0, 45.0, 6.5,
                SleepStage.LIGHT, 4200, ActivityLevel.LIGHT,
                java.time.Instant.now(), null));
        return twin;
    }

    private DigitalTwinState twinWithoutGlucose() {
        PatientId id = PatientId.random();
        return DigitalTwinState.create(id);
    }

    private DigitalTwinState staleTwin() {
        DigitalTwinState twin = activeTwin();
        twin.markStale();
        return twin;
    }

    private DigitalTwinState archivedTwin() {
        DigitalTwinState twin = activeTwin();
        twin.archive();
        return twin;
    }
}
