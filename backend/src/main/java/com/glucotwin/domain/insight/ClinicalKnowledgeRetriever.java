package com.glucotwin.domain.insight;

import java.util.List;

/**
 * Port interface for retrieving general clinical knowledge evidence relevant to
 * a set of evidence topics identified during patient risk analysis.
 *
 * <p>Contract:
 * <ul>
 *   <li>Returns only {@link ClinicalKnowledgeEvidence} items with provenance=CLINICAL_KNOWLEDGE.
 *   <li>Never modifies patient data, Digital Twin, predictions, or simulations.
 *   <li>Returns an empty list when no relevant knowledge exists — never throws for zero results.
 *   <li>Result count is bounded by {@code maxResults}.
 *   <li>Results are deterministically ranked (same topics always return same order).
 *   <li>Patient-specific values are never inserted into the knowledge corpus.
 * </ul>
 */
public interface ClinicalKnowledgeRetriever {

    /**
     * Retrieve general clinical knowledge relevant to the given topics.
     *
     * @param topics      normalised topic strings extracted from contributing factors
     *                    (e.g. "cgm_current", "hba1c_latest", "activity_level")
     * @param maxResults  maximum number of knowledge items to return (must be > 0)
     * @return bounded, deterministically ranked list of knowledge evidence;
     *         never null, may be empty
     */
    List<ClinicalKnowledgeEvidence> retrieve(List<String> topics, int maxResults);
}
