package com.glucotwin.domain.insight;

import java.util.Objects;

/**
 * A single item of retrieved general clinical knowledge evidence.
 *
 * <p>Provenance is always {@code CLINICAL_KNOWLEDGE} — this is general educational
 * context, never patient-specific data, diagnosis, or treatment recommendation.
 *
 * <p>Sources are versioned and drawn from the local knowledge corpus only.
 * No external AI services or web APIs are used.
 * Patient-specific values are never stored here.
 */
public record ClinicalKnowledgeEvidence(
        String knowledgeId,
        String title,
        String sourceName,
        String sourceReference,
        String version,
        String topic,
        String excerpt,
        String provenance) {

    /** Canonical provenance label for all clinical knowledge items. */
    public static final String PROVENANCE_CLINICAL_KNOWLEDGE = "CLINICAL_KNOWLEDGE";

    public ClinicalKnowledgeEvidence {
        Objects.requireNonNull(knowledgeId,      "knowledgeId must not be null");
        Objects.requireNonNull(title,            "title must not be null");
        Objects.requireNonNull(sourceName,       "sourceName must not be null");
        Objects.requireNonNull(topic,            "topic must not be null");
        Objects.requireNonNull(excerpt,          "excerpt must not be null");
        Objects.requireNonNull(provenance,       "provenance must not be null");
        if (!PROVENANCE_CLINICAL_KNOWLEDGE.equals(provenance)) {
            throw new IllegalArgumentException(
                    "ClinicalKnowledgeEvidence provenance must be CLINICAL_KNOWLEDGE, got: "
                    + provenance);
        }
        if (knowledgeId.isBlank()) throw new IllegalArgumentException("knowledgeId must not be blank");
        if (title.isBlank())       throw new IllegalArgumentException("title must not be blank");
        if (topic.isBlank())       throw new IllegalArgumentException("topic must not be blank");
        if (excerpt.isBlank())     throw new IllegalArgumentException("excerpt must not be blank");
    }

    /** Factory that enforces CLINICAL_KNOWLEDGE provenance. */
    public static ClinicalKnowledgeEvidence of(
            String knowledgeId, String title, String sourceName,
            String sourceReference, String version, String topic, String excerpt) {
        return new ClinicalKnowledgeEvidence(
                knowledgeId, title, sourceName, sourceReference,
                version, topic, excerpt, PROVENANCE_CLINICAL_KNOWLEDGE);
    }
}
