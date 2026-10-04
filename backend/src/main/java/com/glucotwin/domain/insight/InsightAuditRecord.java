package com.glucotwin.domain.insight;

import com.glucotwin.domain.prediction.RiskCategory;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable domain value object representing a clinical insight audit event.
 *
 * <p>Fields map 1:1 to the existing {@code prediction_audit_log} table (V7 migration).
 * The {@code patientIdRef} is always SHA-256(patientId) — never the raw UUID.
 *
 * <p>The metadata fields are separated out as first-class fields so the application
 * layer can construct and validate them without depending on infrastructure serialisation.
 */
public record InsightAuditRecord(
        /** SHA-256(patientId) — never the raw UUID. */
        String patientIdRef,
        /** The latest prediction ID from the generated insight. */
        UUID latestPredictionId,
        /** Always "CLINICAL_INSIGHT_GENERATED". */
        String eventType,
        /** CLINICIAN or ADMIN role from Spring Security context. May be null. */
        String actorRole,
        /** MDC traceId, when present. May be null. */
        String traceId,
        /** Risk category from the insight. */
        RiskCategory riskCategory,
        /** Spike probability from the insight (0–1). */
        double spikeProbability,
        /** Twin state version at the time of insight generation. */
        int twinStateVersion,
        /** Number of CLINICAL_KNOWLEDGE evidence items retrieved (Phase 9). */
        int knowledgeItemCount,
        /** Data provenance label — always "OBSERVED+PREDICTED". */
        String dataProvenance,
        /** ISO-8601 UTC timestamp of insight generation. */
        Instant generatedAt) {

    public static final String EVENT_TYPE = "CLINICAL_INSIGHT_GENERATED";

    public InsightAuditRecord {
        Objects.requireNonNull(patientIdRef, "patientIdRef must not be null");
        Objects.requireNonNull(eventType,    "eventType must not be null");
        Objects.requireNonNull(riskCategory, "riskCategory must not be null");
        Objects.requireNonNull(dataProvenance, "dataProvenance must not be null");
        Objects.requireNonNull(generatedAt,  "generatedAt must not be null");
        if (patientIdRef.isBlank()) {
            throw new IllegalArgumentException("patientIdRef must not be blank");
        }
        if (!EVENT_TYPE.equals(eventType)) {
            throw new IllegalArgumentException(
                    "eventType must be " + EVENT_TYPE + ", got: " + eventType);
        }
        if (knowledgeItemCount < 0) {
            throw new IllegalArgumentException("knowledgeItemCount must be >= 0");
        }
    }
}
