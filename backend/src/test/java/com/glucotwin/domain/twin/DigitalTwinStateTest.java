package com.glucotwin.domain.twin;

import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.wearable.WearableEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class DigitalTwinStateTest {

    private PatientId patientId;

    @BeforeEach
    void setUp() {
        patientId = PatientId.random();
    }

    @Test
    void create_setsInitialStateCorrectly() {
        var twin = DigitalTwinState.create(patientId);

        assertThat(twin.getPatientId()).isEqualTo(patientId);
        assertThat(twin.getStatus()).isEqualTo(TwinStatus.INITIALISED);
        assertThat(twin.getTwinVersion()).isEqualTo(1);
        assertThat(twin.getStaticLayer()).isNotNull();
        assertThat(twin.getDynamicLayer()).isNotNull();
    }

    @Test
    void applyEhrRecord_incrementsVersion() {
        var twin = DigitalTwinState.create(patientId);
        int initialVersion = twin.getTwinVersion();

        twin.applyEhrRecord(buildEhrRecord());

        assertThat(twin.getTwinVersion()).isEqualTo(initialVersion + 1);
    }

    @Test
    void applyEhrRecord_updatesStaticLayer() {
        var twin = DigitalTwinState.create(patientId);

        twin.applyEhrRecord(buildEhrRecord());

        assertThat(twin.getStaticLayer().sex()).isEqualTo(Sex.MALE);
        assertThat(twin.getStaticLayer().bmi()).isEqualTo(28.5);
    }

    @Test
    void applyWearableEvent_incrementsVersion() {
        var twin = DigitalTwinState.create(patientId);
        int initialVersion = twin.getTwinVersion();

        twin.applyWearableEvent(buildWearableEvent());

        assertThat(twin.getTwinVersion()).isEqualTo(initialVersion + 1);
    }

    @Test
    void applyWearableEvent_transitionsStatusInitialisedToActive() {
        var twin = DigitalTwinState.create(patientId);
        assertThat(twin.getStatus()).isEqualTo(TwinStatus.INITIALISED);

        twin.applyWearableEvent(buildWearableEvent());

        assertThat(twin.getStatus()).isEqualTo(TwinStatus.ACTIVE);
    }

    @Test
    void applyWearableEvent_transitionsStatusStaleToActive() {
        var twin = DigitalTwinState.create(patientId);
        twin.applyWearableEvent(buildWearableEvent()); // INITIALISED → ACTIVE
        twin.markStale();                               // ACTIVE → STALE
        assertThat(twin.getStatus()).isEqualTo(TwinStatus.STALE);

        twin.applyWearableEvent(buildWearableEvent());  // STALE → ACTIVE

        assertThat(twin.getStatus()).isEqualTo(TwinStatus.ACTIVE);
    }

    @Test
    void applyWearableEvent_buildsCgmHistory() {
        var twin = DigitalTwinState.create(patientId);

        for (int i = 0; i < 5; i++) {
            twin.applyWearableEvent(buildWearableEvent());
        }

        assertThat(twin.getDynamicLayer().cgmHistory()).hasSize(5);
    }

    @Test
    void applyWearableEvent_cgmHistoryCapsMAtMaxSize() {
        var twin = DigitalTwinState.create(patientId);

        for (int i = 0; i < DynamicLayer.MAX_CGM_HISTORY + 5; i++) {
            twin.applyWearableEvent(buildWearableEvent());
        }

        assertThat(twin.getDynamicLayer().cgmHistory()).hasSize(DynamicLayer.MAX_CGM_HISTORY);
    }

    @Test
    void markStale_setsStatusToStale() {
        var twin = DigitalTwinState.create(patientId);
        twin.applyWearableEvent(buildWearableEvent());

        twin.markStale();

        assertThat(twin.getStatus()).isEqualTo(TwinStatus.STALE);
    }

    @Test
    void markStale_isNoOpIfAlreadyStale() {
        var twin = DigitalTwinState.create(patientId);
        twin.applyWearableEvent(buildWearableEvent());
        twin.markStale();
        int versionAfterFirstStale = twin.getTwinVersion();

        twin.markStale(); // second call

        assertThat(twin.getTwinVersion()).isEqualTo(versionAfterFirstStale);
    }

    @Test
    void markStale_isNoOpIfArchived() {
        var twin = DigitalTwinState.create(patientId);
        twin.applyWearableEvent(buildWearableEvent());
        twin.archive();

        assertThatNoException().isThrownBy(twin::markStale);
        assertThat(twin.getStatus()).isEqualTo(TwinStatus.ARCHIVED);
    }

    @Test
    void archive_setsStatusArchived() {
        var twin = DigitalTwinState.create(patientId);

        twin.archive();

        assertThat(twin.getStatus()).isEqualTo(TwinStatus.ARCHIVED);
    }

    @Test
    void archive_throwsIfCalledTwice() {
        var twin = DigitalTwinState.create(patientId);
        twin.archive();

        assertThatThrownBy(twin::archive)
                .isInstanceOf(TwinStateArchivedError.class);
    }

    @Test
    void applyEhrRecord_throwsIfArchived() {
        var twin = DigitalTwinState.create(patientId);
        twin.archive();

        assertThatThrownBy(() -> twin.applyEhrRecord(buildEhrRecord()))
                .isInstanceOf(TwinStateArchivedError.class);
    }

    @Test
    void applyWearableEvent_throwsIfArchived() {
        var twin = DigitalTwinState.create(patientId);
        twin.archive();

        assertThatThrownBy(() -> twin.applyWearableEvent(buildWearableEvent()))
                .isInstanceOf(TwinStateArchivedError.class);
    }

    @Test
    void snapshot_returnsImmutableCopyWithSameValues() {
        var twin = DigitalTwinState.create(patientId);
        twin.applyWearableEvent(buildWearableEvent());

        var snapshot = twin.snapshot();

        assertThat(snapshot.patientId()).isEqualTo(patientId);
        assertThat(snapshot.twinVersion()).isEqualTo(twin.getTwinVersion());
        assertThat(snapshot.status()).isEqualTo(twin.getStatus());
    }

    @Test
    void twinVersion_incrementsMonotonically() {
        var twin = DigitalTwinState.create(patientId);

        twin.applyEhrRecord(buildEhrRecord());
        twin.applyWearableEvent(buildWearableEvent());
        twin.applyWearableEvent(buildWearableEvent());
        twin.markStale();

        assertThat(twin.getTwinVersion()).isEqualTo(5); // initial=1, +4 mutations
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private EhrRecord buildEhrRecord() {
        return new EhrRecord(UUID.randomUUID(), patientId,
                LocalDate.of(1975, 6, 15), Sex.MALE, 28.5,
                LocalDate.of(2015, 3, 1), 7.2, 6.1,
                List.of(), List.of());
    }

    private WearableEvent buildWearableEvent() {
        return new WearableEvent(UUID.randomUUID(), patientId,
                8.4, 72.0, 45.0, 6.5,
                SleepStage.LIGHT, 4200, ActivityLevel.LIGHT,
                Instant.now(), "trace-test");
    }
}
