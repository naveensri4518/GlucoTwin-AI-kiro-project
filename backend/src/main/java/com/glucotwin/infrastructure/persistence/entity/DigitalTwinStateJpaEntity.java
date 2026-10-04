package com.glucotwin.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "digital_twin_states")
@Getter @Setter @NoArgsConstructor
public class DigitalTwinStateJpaEntity {

    @Id
    @Column(name = "patient_id", updatable = false, nullable = false)
    private UUID patientId;

    @Column(name = "twin_version", nullable = false)
    private int twinVersion;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "glucose_reading")
    private Double glucoseReading;

    @Column(name = "heart_rate")
    private Double heartRate;

    @Column(name = "hrv")
    private Double hrv;

    @Column(name = "sleep_duration")
    private Double sleepDuration;

    @Column(name = "sleep_stage", length = 10)
    private String sleepStage;

    @Column(name = "step_count")
    private Integer stepCount;

    @Column(name = "activity_level", length = 20)
    private String activityLevel;

    @Column(name = "wearable_event_at")
    private Instant wearableEventAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cgm_history", columnDefinition = "jsonb")
    private List<CgmReadingJson> cgmHistory;

    @Column(name = "data_quality_flags", columnDefinition = "TEXT[]")
    private String[] dataQualityFlags;

    @Column(name = "last_updated_at", nullable = false)
    private Instant lastUpdatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** JPA optimistic lock version — maps to the DB 'version' column. */
    @Version
    @Column(name = "version", nullable = false)
    private int jpaVersion;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        lastUpdatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        lastUpdatedAt = Instant.now();
    }

    /** Embedded JSON representation of a CGM reading for JSONB storage. */
    public record CgmReadingJson(double value, Instant timestamp) {}
}
