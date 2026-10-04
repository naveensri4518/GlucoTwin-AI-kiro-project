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

/**
 * JPA entity for the simulations table.
 * Separate from glucose_predictions — never mixed (design §15.4, §16).
 */
@Entity
@Table(name = "simulations")
@Getter @Setter @NoArgsConstructor
public class SimulationResultJpaEntity {

    @Id
    @Column(name = "simulation_id", updatable = false, nullable = false)
    private UUID simulationId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "twin_state_version", nullable = false)
    private int twinStateVersion;

    // Scenario inputs as JSONB
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scenario_inputs", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> scenarioInputs;

    @Column(name = "spike_probability", nullable = false)
    private double spikeProbability;

    @Column(name = "risk_category", nullable = false, length = 20)
    private String riskCategory;

    @Column(name = "ci_low", nullable = false)
    private double ciLow;

    @Column(name = "ci_high", nullable = false)
    private double ciHigh;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contributing_factors", columnDefinition = "jsonb")
    private List<Map<String, Object>> contributingFactors;

    @Column(name = "prediction_horizon_hrs", nullable = false)
    private int predictionHorizonHrs;

    @Column(name = "model_version", nullable = false, length = 50)
    private String modelVersion;

    @Column(name = "data_quality_warnings", columnDefinition = "TEXT[]")
    private String[] dataQualityWarnings;

    @Column(name = "delta_vs_baseline")
    private Double deltaVsBaseline;

    @Column(name = "simulated_at", nullable = false)
    private Instant simulatedAt;

    // Always SIMULATED — enforced at DB level by CHECK constraint
    @Column(name = "data_provenance", nullable = false, length = 20)
    private String dataProvenance = "SIMULATED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        // Hard-enforce provenance at entity level
        this.dataProvenance = "SIMULATED";
    }
}
