package com.glucotwin.domain.prediction;

import com.glucotwin.domain.shared.DataProvenance;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.shared.PredictionId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entity representing a single prediction produced for a patient.
 *
 * <p>Lifecycle: PENDING → COMPLETED or FAILED. Once in a terminal state, no further mutation.
 */
public class PredictionRecord {

    /** Fixed prediction horizon for v1. */
    public static final int PREDICTION_HORIZON_HOURS = 2;

    private final PredictionId predictionId;
    private final PatientId patientId;
    private PredictionStatus status;
    private final Instant predictedAt;
    private final int predictionHorizonHours;

    // Set on COMPLETED
    private Double spikeProbability;
    private RiskCategory riskCategory;
    private ConfidenceInterval confidenceInterval;
    private List<ContributingFactor> topContributingFactors;

    // Always PREDICTED for model output
    private final DataProvenance dataProvenance;

    private final int twinStateVersion;
    private String modelVersion;
    private List<String> dataQualityWarnings;

    // Set on FAILED
    private String failureReason;

    // Stored for audit
    private FeatureVector featureVector;

    // Triggered by AUTO (wearable event) or MANUAL (on-demand)
    private final String triggeredBy;

    private PredictionRecord(PredictionId predictionId, PatientId patientId,
                              int twinStateVersion, String modelVersion, String triggeredBy) {
        this.predictionId = Objects.requireNonNull(predictionId);
        this.patientId = Objects.requireNonNull(patientId);
        this.status = PredictionStatus.PENDING;
        this.predictedAt = Instant.now();
        this.predictionHorizonHours = PREDICTION_HORIZON_HOURS;
        this.twinStateVersion = twinStateVersion;
        this.modelVersion = Objects.requireNonNull(modelVersion);
        this.dataProvenance = DataProvenance.PREDICTED;
        this.dataQualityWarnings = new ArrayList<>();
        this.triggeredBy = Objects.requireNonNull(triggeredBy);
    }

    /** Full-args constructor for JPA reconstitution. */
    public PredictionRecord(
            PredictionId predictionId, PatientId patientId, PredictionStatus status,
            Instant predictedAt, int predictionHorizonHours,
            Double spikeProbability, RiskCategory riskCategory,
            ConfidenceInterval confidenceInterval, List<ContributingFactor> topContributingFactors,
            DataProvenance dataProvenance, int twinStateVersion, String modelVersion,
            List<String> dataQualityWarnings, String failureReason,
            FeatureVector featureVector, String triggeredBy) {
        this.predictionId = predictionId;
        this.patientId = patientId;
        this.status = status;
        this.predictedAt = predictedAt;
        this.predictionHorizonHours = predictionHorizonHours;
        this.spikeProbability = spikeProbability;
        this.riskCategory = riskCategory;
        this.confidenceInterval = confidenceInterval;
        this.topContributingFactors = topContributingFactors != null ? new ArrayList<>(topContributingFactors) : new ArrayList<>();
        this.dataProvenance = dataProvenance != null ? dataProvenance : DataProvenance.PREDICTED;
        this.twinStateVersion = twinStateVersion;
        this.modelVersion = modelVersion;
        this.dataQualityWarnings = dataQualityWarnings != null ? new ArrayList<>(dataQualityWarnings) : new ArrayList<>();
        this.failureReason = failureReason;
        this.featureVector = featureVector;
        this.triggeredBy = triggeredBy;
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    public static PredictionRecord pending(PatientId patientId, int twinStateVersion,
                                           String modelVersion, String triggeredBy) {
        return new PredictionRecord(PredictionId.random(), patientId, twinStateVersion,
                modelVersion, triggeredBy);
    }

    // -------------------------------------------------------------------------
    // Domain behaviour
    // -------------------------------------------------------------------------

    /** Transition to COMPLETED with all output fields set. */
    public void complete(PredictionResult result, RiskCategory riskCategory,
                         List<String> qualityWarnings, FeatureVector featureVector) {
        assertPending();
        this.spikeProbability = result.spikeProbability();
        this.confidenceInterval = result.confidenceInterval();
        this.topContributingFactors = new ArrayList<>(result.topContributingFactors());
        this.riskCategory = Objects.requireNonNull(riskCategory);
        this.modelVersion = result.modelVersion();
        this.featureVector = featureVector;

        // Merge imputed fields from ML result into quality warnings
        List<String> allWarnings = new ArrayList<>(qualityWarnings != null ? qualityWarnings : List.of());
        for (String imputed : result.imputedFields()) {
            allWarnings.add("IMPUTED_FIELD:" + imputed);
        }
        this.dataQualityWarnings = allWarnings;
        this.status = PredictionStatus.COMPLETED;
    }

    /** Transition to FAILED with a failure reason. */
    public void fail(String reason) {
        assertPending();
        this.failureReason = Objects.requireNonNull(reason);
        this.status = PredictionStatus.FAILED;
    }

    private void assertPending() {
        if (this.status != PredictionStatus.PENDING) {
            throw new IllegalStateException(
                    "PredictionRecord is already in terminal state: " + this.status);
        }
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public PredictionId getPredictionId() { return predictionId; }
    public PatientId getPatientId() { return patientId; }
    public PredictionStatus getStatus() { return status; }
    public Instant getPredictedAt() { return predictedAt; }
    public int getPredictionHorizonHours() { return predictionHorizonHours; }
    public Double getSpikeProbability() { return spikeProbability; }
    public RiskCategory getRiskCategory() { return riskCategory; }
    public ConfidenceInterval getConfidenceInterval() { return confidenceInterval; }
    public List<ContributingFactor> getTopContributingFactors() {
        return topContributingFactors != null ? List.copyOf(topContributingFactors) : List.of();
    }
    public DataProvenance getDataProvenance() { return dataProvenance; }
    public int getTwinStateVersion() { return twinStateVersion; }
    public String getModelVersion() { return modelVersion; }
    public List<String> getDataQualityWarnings() {
        return dataQualityWarnings != null ? List.copyOf(dataQualityWarnings) : List.of();
    }
    public String getFailureReason() { return failureReason; }
    public FeatureVector getFeatureVector() { return featureVector; }
    public String getTriggeredBy() { return triggeredBy; }
}
