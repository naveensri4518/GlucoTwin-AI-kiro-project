package com.glucotwin.test.factory;

import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.twin.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Factory for creating test instances of domain objects with sensible synthetic defaults.
 * All generated data is purely synthetic — no real patient information.
 */
public final class TwinStateSnapshotFactory {

    private TwinStateSnapshotFactory() {}

    /** Creates a fully-populated snapshot with all fields present. */
    public static TwinStateSnapshot create() {
        return createWithGlucose(8.4);
    }

    /** Creates a snapshot with the given glucose reading. */
    public static TwinStateSnapshot createWithGlucose(double glucoseReading) {
        return createBuilder().withGlucoseReading(glucoseReading).build();
    }

    /** Creates a snapshot with STALE status. */
    public static TwinStateSnapshot createStale() {
        return createBuilder().withStatus(TwinStatus.STALE).build();
    }

    /** Creates a snapshot with no glucose reading (triggers GlucoseReadingRequiredException). */
    public static TwinStateSnapshot createWithoutGlucose() {
        DynamicLayer dynamic = new DynamicLayer(
                null, 72.0, 45.0, 6.5, SleepStage.LIGHT,
                4200, ActivityLevel.LIGHT, Instant.now(), List.of(), Set.of(),
                com.glucotwin.domain.shared.DataProvenance.OBSERVED);
        return new TwinStateSnapshot(PatientId.random(), 1, TwinStatus.ACTIVE,
                buildStaticLayer(), dynamic, Instant.now());
    }

    public static Builder createBuilder() {
        return new Builder();
    }

    public static class Builder {
        private PatientId patientId = PatientId.random();
        private int twinVersion = 3;
        private TwinStatus status = TwinStatus.ACTIVE;
        private Double glucoseReading = 8.4;

        public Builder withPatientId(PatientId id) { this.patientId = id; return this; }
        public Builder withTwinVersion(int v) { this.twinVersion = v; return this; }
        public Builder withStatus(TwinStatus s) { this.status = s; return this; }
        public Builder withGlucoseReading(Double g) { this.glucoseReading = g; return this; }

        public TwinStateSnapshot build() {
            DynamicLayer dynamic = new DynamicLayer(
                    glucoseReading, 72.0, 45.0, 6.5, SleepStage.LIGHT,
                    4200, ActivityLevel.LIGHT, Instant.now(), buildCgmHistory(), Set.of(),
                    com.glucotwin.domain.shared.DataProvenance.OBSERVED);
            return new TwinStateSnapshot(patientId, twinVersion, status,
                    buildStaticLayer(), dynamic, Instant.now());
        }

        private List<CgmReading> buildCgmHistory() {
            Instant base = Instant.now().minusSeconds(3600);
            return List.of(
                    new CgmReading(7.0, base),
                    new CgmReading(7.5, base.plusSeconds(600)),
                    new CgmReading(8.0, base.plusSeconds(1200)),
                    new CgmReading(8.4, base.plusSeconds(1800)));
        }
    }

    private static StaticLayer buildStaticLayer() {
        return new StaticLayer(
                LocalDate.of(1975, 6, 15), Sex.MALE, 28.5,
                LocalDate.of(2015, 3, 1), 7.2, 6.1,
                List.of(), List.of(),
                com.glucotwin.domain.shared.DataProvenance.OBSERVED);
    }

    private static List<CgmReading> buildCgmHistory() {
        Instant base = Instant.now().minusSeconds(3600);
        return List.of(
                new CgmReading(7.0, base),
                new CgmReading(7.5, base.plusSeconds(600)),
                new CgmReading(8.0, base.plusSeconds(1200)),
                new CgmReading(8.4, base.plusSeconds(1800)));
    }
}
