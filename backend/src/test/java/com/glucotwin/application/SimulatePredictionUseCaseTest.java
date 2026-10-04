package com.glucotwin.application;

import com.glucotwin.domain.prediction.*;
import com.glucotwin.domain.shared.DataProvenance;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.shared.ResourceNotFoundException;
import com.glucotwin.domain.shared.ValidationException;
import com.glucotwin.domain.simulation.SimulationResult;
import com.glucotwin.domain.simulation.SimulationScenario;
import com.glucotwin.domain.twin.*;
import com.glucotwin.domain.wearable.WearableEvent;
import com.glucotwin.infrastructure.config.GlucoTwinProperties;
import com.glucotwin.test.mock.MockPredictionModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimulatePredictionUseCaseTest {

    @Mock private DigitalTwinRepository digitalTwinRepository;
    @Mock private PredictionRepository predictionRepository;

    private MockPredictionModel mockModel;
    private SimulatePredictionUseCase useCase;

    private PatientId patientId;

    @BeforeEach
    void setUp() {
        patientId = PatientId.random();
        mockModel = new MockPredictionModel(0.65);
        GlucoTwinProperties props = new GlucoTwinProperties();
        useCase = new SimulatePredictionUseCase(
                digitalTwinRepository, predictionRepository, mockModel, props);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void execute_happyPath_returnsSimulatedResult() {
        when(digitalTwinRepository.findByPatientId(patientId)).thenReturn(Optional.of(activeTwin()));
        when(predictionRepository.findByPatientId(any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        SimulationScenario scenario = new SimulationScenario(90.0, ActivityLevel.MODERATE, true);
        SimulationResult result = useCase.execute(patientId, scenario);

        assertThat(result).isNotNull();
        assertThat(result.spikeProbability()).isBetween(0.0, 1.0);
        assertThat(result.riskCategory()).isNotNull();
        assertThat(result.modelVersion()).isEqualTo("mock-v1.0.0");
        assertThat(result.predictionHorizonHours()).isEqualTo(2);
    }

    @Test
    void execute_dataProvenanceIsAlwaysSIMULATED() {
        when(digitalTwinRepository.findByPatientId(patientId)).thenReturn(Optional.of(activeTwin()));
        when(predictionRepository.findByPatientId(any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        SimulationResult result = useCase.execute(patientId,
                new SimulationScenario(60.0, null, null));

        assertThat(result.dataProvenance()).isEqualTo(DataProvenance.SIMULATED);
    }

    @Test
    void execute_realTwinStateIsNeverModified() {
        DigitalTwinState twin = activeTwin();
        int versionBefore = twin.getTwinVersion();
        ActivityLevel activityBefore = twin.getDynamicLayer().activityLevel();

        when(digitalTwinRepository.findByPatientId(patientId)).thenReturn(Optional.of(twin));
        when(predictionRepository.findByPatientId(any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        useCase.execute(patientId,
                new SimulationScenario(null, ActivityLevel.VIGOROUS, null));

        // Twin version must not change
        assertThat(twin.getTwinVersion()).isEqualTo(versionBefore);
        // Twin activity must not change to the scenario value
        assertThat(twin.getDynamicLayer().activityLevel()).isEqualTo(activityBefore);
        // JPA save must NOT be called on the twin repository
        verify(digitalTwinRepository, never()).save(any());
    }

    @Test
    void execute_predictionModelIsCalledWithMutatedSnapshot() {
        when(digitalTwinRepository.findByPatientId(patientId)).thenReturn(Optional.of(activeTwin()));
        when(predictionRepository.findByPatientId(any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        useCase.execute(patientId,
                new SimulationScenario(null, ActivityLevel.VIGOROUS, null));

        assertThat(mockModel.getCallCount()).isEqualTo(1);
        // The snapshot passed to the model has VIGOROUS activity
        var snap = mockModel.getRecordedCalls().get(0);
        assertThat(snap.dynamicLayer().activityLevel()).isEqualTo(ActivityLevel.VIGOROUS);
    }

    @Test
    void execute_medicationNotTaken_addsWarningToSnapshot() {
        when(digitalTwinRepository.findByPatientId(patientId)).thenReturn(Optional.of(activeTwin()));
        when(predictionRepository.findByPatientId(any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        useCase.execute(patientId, new SimulationScenario(null, null, false));

        var snap = mockModel.getRecordedCalls().get(0);
        assertThat(snap.dynamicLayer().dataQualityWarnings())
                .contains("SCENARIO_MEDICATION_NOT_TAKEN");
    }

    @Test
    void execute_mealCarbs_addsWarningToSnapshot() {
        when(digitalTwinRepository.findByPatientId(patientId)).thenReturn(Optional.of(activeTwin()));
        when(predictionRepository.findByPatientId(any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        useCase.execute(patientId, new SimulationScenario(120.0, null, null));

        var snap = mockModel.getRecordedCalls().get(0);
        assertThat(snap.dynamicLayer().dataQualityWarnings())
                .anyMatch(w -> w.startsWith("SCENARIO_MEAL_CARBS_"));
    }

    @Test
    void execute_deltaVsBaselineUsesRealPrediction() {
        // Set up a real completed prediction with probability 0.50
        PredictionRecord realPred = PredictionRecord.pending(patientId, 1, "xgboost-v1.0.0", "AUTO");
        realPred.complete(
                new PredictionResult(0.50, new ConfidenceInterval(0.40, 0.60),
                        java.util.List.of(), "xgboost-v1.0.0", java.util.List.of()),
                RiskCategory.MODERATE, java.util.List.of(), null);

        when(digitalTwinRepository.findByPatientId(patientId)).thenReturn(Optional.of(activeTwin()));
        when(predictionRepository.findByPatientId(any(), any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        java.util.List.of(realPred)));

        mockModel.withProbability(0.65);
        SimulationResult result = useCase.execute(patientId,
                new SimulationScenario(60.0, null, null));

        // delta = 0.65 - 0.50 = 0.15 (±floating point tolerance)
        assertThat(result.deltaVsBaseline()).isNotNull();
        assertThat(result.deltaVsBaseline()).isCloseTo(0.15, within(0.001));
    }

    @Test
    void execute_deltaVsBaselineIsNullWhenNoPriorPrediction() {
        when(digitalTwinRepository.findByPatientId(patientId)).thenReturn(Optional.of(activeTwin()));
        when(predictionRepository.findByPatientId(any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        SimulationResult result = useCase.execute(patientId,
                new SimulationScenario(60.0, null, null));

        assertThat(result.deltaVsBaseline()).isNull();
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    void execute_emptyScenario_throwsValidationException() {
        assertThatThrownBy(() ->
                useCase.execute(patientId, new SimulationScenario(null, null, null)))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getErrorCode()).isEqualTo("EMPTY_SCENARIO");
                    assertThat(ve.getField()).isEqualTo("scenario");
                });
    }

    @Test
    void execute_patientNotFound_throws404() {
        when(digitalTwinRepository.findByPatientId(patientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                useCase.execute(patientId, new SimulationScenario(60.0, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void execute_archivedTwin_throwsTwinStateArchivedError() {
        DigitalTwinState twin = activeTwin();
        twin.archive();
        when(digitalTwinRepository.findByPatientId(patientId)).thenReturn(Optional.of(twin));

        assertThatThrownBy(() ->
                useCase.execute(patientId, new SimulationScenario(60.0, null, null)))
                .isInstanceOf(TwinStateArchivedError.class);
    }

    @Test
    void execute_noGlucoseReading_throwsGlucoseReadingRequired() {
        DigitalTwinState twin = DigitalTwinState.create(patientId); // INITIALISED, no wearable
        when(digitalTwinRepository.findByPatientId(patientId)).thenReturn(Optional.of(twin));

        assertThatThrownBy(() ->
                useCase.execute(patientId, new SimulationScenario(60.0, null, null)))
                .isInstanceOf(GlucoseReadingRequiredException.class);
    }

    // ── Safety: result is never PREDICTED or OBSERVED ─────────────────────────

    @Test
    void execute_resultNeverPredictedProvenance() {
        when(digitalTwinRepository.findByPatientId(patientId)).thenReturn(Optional.of(activeTwin()));
        when(predictionRepository.findByPatientId(any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        SimulationResult result = useCase.execute(patientId,
                new SimulationScenario(null, ActivityLevel.LIGHT, null));

        assertThat(result.dataProvenance()).isNotEqualTo(DataProvenance.PREDICTED);
        assertThat(result.dataProvenance()).isNotEqualTo(DataProvenance.OBSERVED);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DigitalTwinState activeTwin() {
        DigitalTwinState twin = DigitalTwinState.create(patientId);
        twin.applyWearableEvent(new WearableEvent(UUID.randomUUID(), patientId,
                8.4, 72.0, 45.0, 6.5, SleepStage.LIGHT, 4200, ActivityLevel.LIGHT,
                Instant.now(), null));
        return twin;
    }
}
