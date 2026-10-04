package com.glucotwin.domain.twin;

import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.wearable.WearableEvent;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root for the Digital Twin.
 *
 * <p>Invariants:
 * <ul>
 *   <li>twinVersion increments on every apply* call; never decremented.</li>
 *   <li>Status transitions: INITIALISED → ACTIVE, STALE → ACTIVE, ACTIVE → STALE, any → ARCHIVED.
 *       Cannot un-archive.</li>
 *   <li>lastUpdatedAt is always ≤ current system time.</li>
 * </ul>
 */
public class DigitalTwinState {

    private final PatientId patientId;
    private TwinStatus status;
    private int twinVersion;
    private StaticLayer staticLayer;
    private DynamicLayer dynamicLayer;
    private Instant lastUpdatedAt;
    private final Instant createdAt;

    // Private constructor — use factory method
    private DigitalTwinState(PatientId patientId, Instant createdAt) {
        this.patientId = Objects.requireNonNull(patientId);
        this.status = TwinStatus.INITIALISED;
        this.twinVersion = 1;
        this.staticLayer = StaticLayer.empty();
        this.dynamicLayer = DynamicLayer.empty();
        this.createdAt = createdAt;
        this.lastUpdatedAt = createdAt;
    }

    /** Full-args constructor used by JPA mapper for reconstitution from DB. */
    public DigitalTwinState(
            PatientId patientId,
            TwinStatus status,
            int twinVersion,
            StaticLayer staticLayer,
            DynamicLayer dynamicLayer,
            Instant lastUpdatedAt,
            Instant createdAt) {
        this.patientId = Objects.requireNonNull(patientId);
        this.status = Objects.requireNonNull(status);
        this.twinVersion = twinVersion;
        this.staticLayer = staticLayer != null ? staticLayer : StaticLayer.empty();
        this.dynamicLayer = dynamicLayer != null ? dynamicLayer : DynamicLayer.empty();
        this.lastUpdatedAt = Objects.requireNonNull(lastUpdatedAt);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    /** Factory: creates a new twin for a patient who has never had a twin before. */
    public static DigitalTwinState create(PatientId patientId) {
        return new DigitalTwinState(patientId, Instant.now());
    }

    // -------------------------------------------------------------------------
    // Domain behaviour
    // -------------------------------------------------------------------------

    /**
     * Apply an EHR record to the static layer.
     * Does NOT trigger a prediction — prediction requires wearable context.
     */
    public void applyEhrRecord(EhrRecord ehr) {
        assertNotArchived();
        this.staticLayer = staticLayer.withEhrUpdate(
                ehr.dateOfBirth(), ehr.sex(), ehr.bmi(),
                ehr.diabetesOnsetDate(), ehr.hba1c(), ehr.fastingGlucose(),
                ehr.medications(), ehr.labResults());
        incrementVersion();
    }

    /**
     * Apply a wearable event to the dynamic layer.
     * Transitions status INITIALISED → ACTIVE or STALE → ACTIVE.
     */
    public void applyWearableEvent(WearableEvent event) {
        assertNotArchived();
        this.dynamicLayer = dynamicLayer.applyEvent(
                event.glucoseReading(),
                event.heartRate(),
                event.hrv(),
                event.sleepDuration(),
                event.sleepStage(),
                event.stepCount(),
                event.activityLevel(),
                event.eventTimestamp(),
                null);

        // Transition to ACTIVE from INITIALISED or STALE
        if (this.status == TwinStatus.INITIALISED || this.status == TwinStatus.STALE) {
            this.status = TwinStatus.ACTIVE;
        }
        incrementVersion();
    }

    /**
     * Mark this twin as STALE (no recent wearable data).
     * No-op if already STALE or ARCHIVED.
     */
    public void markStale() {
        if (this.status == TwinStatus.ARCHIVED) return;
        if (this.status != TwinStatus.STALE) {
            this.status = TwinStatus.STALE;
            incrementVersion();
        }
    }

    /**
     * Archive this twin. Freezes all state.
     * @throws TwinStateArchivedError if already archived.
     */
    public void archive() {
        if (this.status == TwinStatus.ARCHIVED) {
            throw new TwinStateArchivedError(patientId.toString());
        }
        this.status = TwinStatus.ARCHIVED;
        incrementVersion();
    }

    /**
     * Returns an immutable snapshot of the current state.
     * Must be called inside a read transaction.
     */
    public TwinStateSnapshot snapshot() {
        return TwinStateSnapshot.from(this);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void assertNotArchived() {
        if (this.status == TwinStatus.ARCHIVED) {
            throw new TwinStateArchivedError(patientId.toString());
        }
    }

    private void incrementVersion() {
        this.twinVersion++;
        this.lastUpdatedAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Getters (no setters — mutations via domain methods only)
    // -------------------------------------------------------------------------

    public PatientId getPatientId() { return patientId; }
    public TwinStatus getStatus() { return status; }
    public int getTwinVersion() { return twinVersion; }
    public StaticLayer getStaticLayer() { return staticLayer; }
    public DynamicLayer getDynamicLayer() { return dynamicLayer; }
    public Instant getLastUpdatedAt() { return lastUpdatedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
