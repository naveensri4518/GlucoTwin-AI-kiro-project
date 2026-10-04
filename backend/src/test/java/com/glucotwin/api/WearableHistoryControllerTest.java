package com.glucotwin.api;

import com.glucotwin.application.GetDigitalTwinStateUseCase;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.shared.ResourceNotFoundException;
import com.glucotwin.domain.twin.*;
import com.glucotwin.domain.wearable.WearableEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WearableHistoryControllerTest {

    @Mock
    private GetDigitalTwinStateUseCase getDigitalTwinStateUseCase;

    @InjectMocks
    private WearableHistoryController controller;

    private PatientId patientId;
    private UUID patientUuid;

    @BeforeEach
    void setUp() {
        patientUuid = UUID.randomUUID();
        patientId = PatientId.of(patientUuid);
    }

    // ── Happy path — readings present ────────────────────────────────────────

    @Test
    void cgmHistory_returnsReadingsWhenPresent() {
        DigitalTwinState twin = twinWithReadings(3);
        when(getDigitalTwinStateUseCase.execute(any())).thenReturn(twin);

        var response = controller.getCgmHistory(patientUuid);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull().hasSize(3);
    }

    @Test
    void cgmHistory_valuesArePreservedExactly() {
        Instant t1 = Instant.parse("2026-10-04T10:00:00Z");
        Instant t2 = Instant.parse("2026-10-04T10:30:00Z");

        DigitalTwinState twin = twinWithReadings(List.of(
                new CgmReading(7.2, t1),
                new CgmReading(9.5, t2)));
        when(getDigitalTwinStateUseCase.execute(any())).thenReturn(twin);

        var body = controller.getCgmHistory(patientUuid).getBody();

        assertThat(body).isNotNull().hasSize(2);
        assertThat(body.get(0).value()).isEqualTo(7.2);
        assertThat(body.get(0).timestamp()).isEqualTo(t1);
        assertThat(body.get(1).value()).isEqualTo(9.5);
        assertThat(body.get(1).timestamp()).isEqualTo(t2);
    }

    @Test
    void cgmHistory_dataProvenanceIsAlwaysOBSERVED() {
        DigitalTwinState twin = twinWithReadings(2);
        when(getDigitalTwinStateUseCase.execute(any())).thenReturn(twin);

        var body = controller.getCgmHistory(patientUuid).getBody();

        assertThat(body).isNotNull()
                .allMatch(r -> "OBSERVED".equals(r.dataProvenance()),
                        "all readings must have dataProvenance = OBSERVED");
    }

    // ── Empty CGM history ─────────────────────────────────────────────────────

    @Test
    void cgmHistory_returnsEmptyListWhenNoCgmHistory() {
        DigitalTwinState twin = DigitalTwinState.create(patientId);
        // INITIALISED twin — no wearable events applied — dynamic layer has empty history
        when(getDigitalTwinStateUseCase.execute(any())).thenReturn(twin);

        var response = controller.getCgmHistory(patientUuid);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull().isEmpty();
    }

    @Test
    void cgmHistory_returnsEmptyListWhenNoDynamicLayer() {
        // A freshly created twin with no events has an empty DynamicLayer.cgmHistory
        DigitalTwinState twin = DigitalTwinState.create(patientId);
        when(getDigitalTwinStateUseCase.execute(any())).thenReturn(twin);

        var response = controller.getCgmHistory(patientUuid);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).isEmpty();
    }

    // ── 404 when patient/twin not found ──────────────────────────────────────

    @Test
    void cgmHistory_propagates404WhenTwinNotFound() {
        when(getDigitalTwinStateUseCase.execute(any()))
                .thenThrow(new ResourceNotFoundException("DigitalTwinState", patientUuid.toString()));

        assertThatThrownBy(() -> controller.getCgmHistory(patientUuid))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(patientUuid.toString());
    }

    // ── Authorization: controller delegates to use case regardless of authz ──
    // (Spring Security filter is not active in unit tests; integration tests cover authz)

    @Test
    void cgmHistory_callsUseCaseWithCorrectPatientId() {
        DigitalTwinState twin = DigitalTwinState.create(patientId);
        when(getDigitalTwinStateUseCase.execute(any())).thenReturn(twin);

        controller.getCgmHistory(patientUuid);

        verify(getDigitalTwinStateUseCase).execute(argThat(id ->
                id.value().equals(patientUuid)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DigitalTwinState twinWithReadings(int count) {
        Instant base = Instant.now().minus(60, ChronoUnit.MINUTES);
        List<CgmReading> readings = java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new CgmReading(7.0 + i * 0.5,
                        base.plus(i * 10L, ChronoUnit.MINUTES)))
                .toList();
        return twinWithReadings(readings);
    }

    private DigitalTwinState twinWithReadings(List<CgmReading> readings) {
        DigitalTwinState twin = DigitalTwinState.create(patientId);
        // Apply synthetic wearable events to populate CGM history
        Instant base = readings.isEmpty() ? Instant.now() : readings.get(0).timestamp();
        for (CgmReading r : readings) {
            twin.applyWearableEvent(new WearableEvent(
                    UUID.randomUUID(), patientId,
                    r.value(), 72.0, null, null, null, null, null,
                    r.timestamp(), null));
        }
        return twin;
    }
}
