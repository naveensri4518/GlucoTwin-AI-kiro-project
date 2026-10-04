package com.glucotwin.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "patient_ehr")
@Getter @Setter @NoArgsConstructor
public class PatientEhrJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ehr_id", updatable = false, nullable = false)
    private UUID ehrId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "sex", nullable = false, length = 10)
    private String sex;

    @Column(name = "bmi")
    private Double bmi;

    @Column(name = "diabetes_onset_date", nullable = false)
    private LocalDate diabetesOnsetDate;

    @Column(name = "hba1c")
    private Double hba1c;

    @Column(name = "fasting_glucose")
    private Double fastingGlucose;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "medications", columnDefinition = "jsonb")
    private List<Map<String, Object>> medications;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "lab_results", columnDefinition = "jsonb")
    private List<Map<String, Object>> labResults;

    @Column(name = "data_source", nullable = false, length = 20)
    private String dataSource = "SYNTHETIC";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = Instant.now();
    }
}
