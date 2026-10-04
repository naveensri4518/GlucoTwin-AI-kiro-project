package com.glucotwin.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "glucose_predictions")
@Getter @Setter @NoArgsConstructor
public class GlucosePredictionJpaEntity {

    @Id
    @Column(name = "prediction_id", updatable = false, nullable = false)
    private UUID predictionId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "predicted_at", nullable = false)
    private Instant predictedAt;

    @Column(name = "prediction_horizon_hrs", nullable = false)
    private int predictionHorizonHrs;

    @Column(name = "spike_probability")
    private Double spikeProbability;

    @Column(name = "risk_category", length = 20)
    private String riskCategory;

    @Column(name = "ci_low")
    private Double ciLow;

    @Column(name = "ci_high")
    private Double ciHigh;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contributing_factors", columnDefinition = "jsonb")
    private List<Map<String, Object>> contributingFactors;

    @Column(name = "data_provenance", nullable = false, length = 20)
    private String dataProvenance;

    @Column(name = "twin_state_version", nullable = false)
    private int twinStateVersion;

    @Column(name = "model_version", nullable = false, length = 50)
    private String modelVersion;

    @Column(name = "data_quality_warnings", columnDefinition = "TEXT[]")
    private String[] dataQualityWarnings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feature_vector", columnDefinition = "jsonb")
    private Map<String, Object> featureVector;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "triggered_by", nullable = false, length = 10)
    private String triggeredBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (predictedAt == null) predictedAt = Instant.now();
    }
}
