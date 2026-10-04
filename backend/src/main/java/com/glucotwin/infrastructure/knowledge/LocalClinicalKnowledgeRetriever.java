package com.glucotwin.infrastructure.knowledge;

import com.glucotwin.domain.insight.ClinicalKnowledgeEvidence;
import com.glucotwin.domain.insight.ClinicalKnowledgeRetriever;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic, local implementation of {@link ClinicalKnowledgeRetriever}.
 *
 * <p>Uses a versioned in-memory corpus of general clinical/educational material
 * appropriate for glucose-management context. No external service, no vector
 * database, no LLM — purely normalised keyword/topic matching with deterministic
 * ranking.
 *
 * <p>Corpus rules (enforced structurally):
 * <ul>
 *   <li>All items contain only general educational information.
 *   <li>No patient-specific data, medication dosage, diagnosis rules, or treatment protocols.
 *   <li>Every item carries provenance = CLINICAL_KNOWLEDGE.
 *   <li>Source references are documented within the project; no fabricated citations.
 * </ul>
 *
 * <p>Content note: The excerpts below are original general educational summaries
 * reflecting well-established physiological concepts in diabetes and metabolic health.
 * They do not reproduce verbatim text from any copyrighted publication.
 */
@Component
@Slf4j
public class LocalClinicalKnowledgeRetriever implements ClinicalKnowledgeRetriever {

    private static final String CORPUS_VERSION = "1.0.0";

    /** Internal representation pairing evidence with its search keywords. */
    private record KnowledgeDocument(
            ClinicalKnowledgeEvidence evidence,
            List<String> searchKeywords) {}

    /** Immutable versioned knowledge corpus — built once at class-load time. */
    private static final List<KnowledgeDocument> CORPUS = buildCorpus();

    @Override
    public List<ClinicalKnowledgeEvidence> retrieve(List<String> topics, int maxResults) {
        if (topics == null || topics.isEmpty()) {
            return List.of();
        }
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be > 0, got: " + maxResults);
        }

        List<String> normTopics = topics.stream()
                .map(t -> t.toLowerCase(Locale.ROOT).replace("_", " ").replace("-", " "))
                .toList();

        // Score each document: count keyword hits across all normalised topics
        record Scored(KnowledgeDocument doc, int score) {}
        List<Scored> scored = new ArrayList<>();
        for (KnowledgeDocument doc : CORPUS) {
            int score = 0;
            for (String kw : doc.searchKeywords()) {
                String normKw = kw.toLowerCase(Locale.ROOT);
                for (String topic : normTopics) {
                    if (topic.contains(normKw) || normKw.contains(topic)) {
                        score++;
                    }
                }
            }
            if (score > 0) {
                scored.add(new Scored(doc, score));
            }
        }

        // Deterministic sort: descending score then ascending knowledgeId
        scored.sort((a, b) -> {
            int c = Integer.compare(b.score(), a.score());
            return c != 0 ? c : a.doc().evidence().knowledgeId()
                    .compareTo(b.doc().evidence().knowledgeId());
        });

        List<ClinicalKnowledgeEvidence> results = scored.stream()
                .limit(maxResults)
                .map(s -> s.doc().evidence())
                .toList();

        log.debug("[KnowledgeRetriever] topics={} candidates={} returned={}",
                topics, scored.size(), results.size());
        return results;
    }

    // ── Corpus ────────────────────────────────────────────────────────────────

    private static List<KnowledgeDocument> buildCorpus() {
        List<KnowledgeDocument> docs = new ArrayList<>();

        docs.add(new KnowledgeDocument(
            ClinicalKnowledgeEvidence.of(
                "K001",
                "Post-Meal Glucose Response and Carbohydrate Intake",
                "GlucoTwin Clinical Knowledge Base",
                "GlucoTwin-KB-v1.0.0, Section 2.1 — Postprandial Glucose Physiology",
                CORPUS_VERSION, "postprandial_glucose",
                "General clinical context: Carbohydrate intake is a primary driver of "
                + "postprandial glucose elevation. The quantity and glycaemic index of "
                + "consumed carbohydrates influence the rate and magnitude of blood glucose "
                + "rise following a meal. Higher carbohydrate loads are generally associated "
                + "with greater glucose excursions in individuals with impaired insulin "
                + "response. This is general educational context — not a dietary recommendation."),
            List.of("meal", "carb", "carbohydrate", "postprandial", "post meal", "food")
        ));

        docs.add(new KnowledgeDocument(
            ClinicalKnowledgeEvidence.of(
                "K002",
                "Physical Activity and Glucose Regulation",
                "GlucoTwin Clinical Knowledge Base",
                "GlucoTwin-KB-v1.0.0, Section 2.2 — Activity and Metabolic Response",
                CORPUS_VERSION, "physical_activity",
                "General clinical context: Physical activity influences glucose utilisation. "
                + "Aerobic and resistance exercise generally promote glucose uptake by skeletal "
                + "muscle, which can reduce circulating glucose levels. Sedentary behaviour is "
                + "associated with reduced insulin sensitivity over time. The magnitude and "
                + "timing of the glucose-lowering effect varies by activity intensity and "
                + "individual metabolic response. This is general educational context — "
                + "not an exercise prescription."),
            List.of("activity", "step", "exercise", "vigorous", "sedentary", "light", "moderate",
                    "activity level", "step count")
        ));

        docs.add(new KnowledgeDocument(
            ClinicalKnowledgeEvidence.of(
                "K003",
                "Sleep Duration, Quality, and Glucose Metabolism",
                "GlucoTwin Clinical Knowledge Base",
                "GlucoTwin-KB-v1.0.0, Section 2.3 — Sleep and Metabolic Health",
                CORPUS_VERSION, "sleep_metabolic",
                "General clinical context: Insufficient or fragmented sleep is associated "
                + "with altered glucose metabolism and reduced insulin sensitivity in "
                + "observational studies. Heart rate variability (HRV) is sometimes used as "
                + "a proxy for autonomic nervous system balance, which influences metabolic "
                + "regulation. This is general educational context — not a clinical recommendation."),
            List.of("sleep", "hrv", "heart rate variability", "rest", "sleep duration")
        ));

        docs.add(new KnowledgeDocument(
            ClinicalKnowledgeEvidence.of(
                "K004",
                "Continuous Glucose Monitoring and Glucose Variability",
                "GlucoTwin Clinical Knowledge Base",
                "GlucoTwin-KB-v1.0.0, Section 2.4 — CGM Interpretation",
                CORPUS_VERSION, "cgm_variability",
                "General clinical context: Continuous glucose monitoring (CGM) provides "
                + "real-time glucose data that captures intra-day variability. Elevated CGM "
                + "readings in the 2-hour post-meal window are commonly used as an indicator "
                + "of postprandial glucose response. CGM slope (rate of change) provides "
                + "additional context about glucose trajectory. This is general educational "
                + "context — patient device values are displayed separately as OBSERVED data."),
            List.of("cgm", "glucose", "reading", "variability", "current glucose",
                    "cgm current", "cgm slope", "glucose slope")
        ));

        docs.add(new KnowledgeDocument(
            ClinicalKnowledgeEvidence.of(
                "K005",
                "HbA1c as a Long-Term Glycaemic Marker",
                "GlucoTwin Clinical Knowledge Base",
                "GlucoTwin-KB-v1.0.0, Section 2.5 — Glycated Haemoglobin",
                CORPUS_VERSION, "hba1c_glycaemic",
                "General clinical context: HbA1c (glycated haemoglobin) reflects average "
                + "blood glucose over approximately 8–12 weeks. It is widely used as a "
                + "long-term glycaemic control marker. Higher HbA1c values are generally "
                + "associated with elevated average glucose. Interpretation alongside "
                + "real-time CGM data provides a fuller picture of glycaemic status. "
                + "This is general educational context — interpretation must involve a clinician."),
            List.of("hba1c", "glycated", "haemoglobin", "hemoglobin", "a1c", "hba1c latest")
        ));

        docs.add(new KnowledgeDocument(
            ClinicalKnowledgeEvidence.of(
                "K006",
                "Body Mass Index and Insulin Sensitivity",
                "GlucoTwin Clinical Knowledge Base",
                "GlucoTwin-KB-v1.0.0, Section 2.6 — BMI and Metabolic Risk",
                CORPUS_VERSION, "bmi_insulin",
                "General clinical context: Body mass index (BMI) is a commonly used "
                + "anthropometric measure associated with metabolic risk. Higher BMI values "
                + "are often correlated with reduced insulin sensitivity in population-level "
                + "studies, though individual variation is substantial. BMI is one of many "
                + "factors considered in metabolic health assessment. This is general "
                + "educational context — not a weight-management recommendation."),
            List.of("bmi", "weight", "insulin", "resistance", "sensitivity", "body mass")
        ));

        docs.add(new KnowledgeDocument(
            ClinicalKnowledgeEvidence.of(
                "K007",
                "Interpreting Glucose Spike Risk Factors",
                "GlucoTwin Clinical Knowledge Base",
                "GlucoTwin-KB-v1.0.0, Section 3.1 — Risk Factor Interpretation",
                CORPUS_VERSION, "risk_factor_interpretation",
                "General clinical context: Machine learning models for glucose spike "
                + "prediction identify feature contributions (often via SHAP values) "
                + "reflecting the statistical relationship between inputs and the predicted "
                + "outcome. These contributions are correlational, not causal clinical "
                + "pathways. Clinical judgment is required to contextualise model outputs "
                + "alongside the full patient history."),
            List.of("risk", "factor", "spike", "probability", "prediction", "contributing",
                    "shap", "feature")
        ));

        docs.add(new KnowledgeDocument(
            ClinicalKnowledgeEvidence.of(
                "K008",
                "Glucose Variability: Key Concepts",
                "GlucoTwin Clinical Knowledge Base",
                "GlucoTwin-KB-v1.0.0, Section 3.2 — Glucose Variability",
                CORPUS_VERSION, "glucose_variability",
                "General clinical context: Glucose variability refers to fluctuations in "
                + "blood glucose over time, captured by metrics such as time-in-range, "
                + "standard deviation, and coefficient of variation. High variability is "
                + "associated with both hypoglycaemia and hyperglycaemia risk. CGM slope "
                + "provides a real-time indicator of glucose trajectory that complements "
                + "instantaneous readings. This is general educational context — specific "
                + "targets should be defined by the clinical team."),
            List.of("variability", "glucose slope", "cgm slope", "time in range",
                    "excursion", "fluctuation")
        ));

        docs.add(new KnowledgeDocument(
            ClinicalKnowledgeEvidence.of(
                "K009",
                "Digital Twins in Clinical Decision Support",
                "GlucoTwin Clinical Knowledge Base",
                "GlucoTwin-KB-v1.0.0, Section 4.1 — Digital Twin Context",
                CORPUS_VERSION, "digital_twin_context",
                "General clinical context: A digital twin in healthcare is a computational "
                + "model that integrates patient data — including wearable device readings, "
                + "electronic health records, and physiological parameters — to simulate or "
                + "predict individual health outcomes. Predictive outputs from digital twins "
                + "are probabilistic estimates intended to support clinical decision-making. "
                + "They do not replace clinical assessment, diagnosis, or treatment decisions."),
            List.of("digital twin", "twin", "model", "prediction", "decision support",
                    "twin state", "simulation")
        ));

        docs.add(new KnowledgeDocument(
            ClinicalKnowledgeEvidence.of(
                "K010",
                "Fasting Glucose as a Metabolic Indicator",
                "GlucoTwin Clinical Knowledge Base",
                "GlucoTwin-KB-v1.0.0, Section 2.7 — Fasting Glucose",
                CORPUS_VERSION, "fasting_glucose",
                "General clinical context: Fasting plasma glucose is a standard marker used "
                + "in diabetes screening and monitoring. It reflects hepatic glucose output "
                + "and basal insulin action. Elevated fasting glucose may indicate impaired "
                + "fasting glucose or diabetes, though diagnosis requires clinical assessment "
                + "and repeat measurement. This is general educational context — "
                + "diagnostic interpretation must involve a qualified clinician."),
            List.of("fasting", "fasting glucose", "basal", "baseline glucose")
        ));

        return List.copyOf(docs);
    }
}
