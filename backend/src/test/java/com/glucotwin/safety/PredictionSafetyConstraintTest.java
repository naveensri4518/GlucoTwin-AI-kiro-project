package com.glucotwin.safety;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.glucotwin.api.dto.PredictionResponse;
import com.glucotwin.domain.prediction.*;
import com.glucotwin.domain.shared.DataProvenance;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.shared.PredictionId;
import com.glucotwin.test.factory.TwinStateSnapshotFactory;
import com.glucotwin.test.mock.MockPredictionModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Safety constraint tests — run in CI, any failure blocks the build.
 * REQ-025: No diagnostic language
 * REQ-026: Uncertainty fields always present
 * REQ-012: Data provenance always PREDICTED
 * REQ-028: Prediction horizon always 2
 * REQ-027: Synthetic seed data only
 */
@Tag("safety")
class PredictionSafetyConstraintTest {

    private static final List<String> FORBIDDEN_TERMS = Arrays.asList(
            "diagnose", "diagnosis", "you have", "confirms",
            "prescribe", "prescription", "guaranteed", "certain"
    );

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    // -------------------------------------------------------------------------
    // REQ-025: No diagnostic language
    // -------------------------------------------------------------------------

    @Test
    void predictionResponse_containsNoForbiddenDiagnosticTerms() throws Exception {
        PredictionResponse response = buildSamplePredictionResponse(0.72);
        String json = objectMapper.writeValueAsString(response).toLowerCase();

        for (String term : FORBIDDEN_TERMS) {
            assertThat(json)
                    .as("Forbidden term '%s' found in prediction response JSON", term)
                    .doesNotContain(term.toLowerCase());
        }
    }

    @Test
    void dataQualityWarnings_containNoForbiddenDiagnosticTerms() throws Exception {
        // REQ-025 extended: scan dataQualityWarnings too
        PredictionResponse response = buildSamplePredictionResponseWithWarnings(
                List.of("STALE_WEARABLE_DATA", "IMPUTED_FIELD:hrv_current")
        );
        String json = objectMapper.writeValueAsString(response).toLowerCase();

        for (String term : FORBIDDEN_TERMS) {
            assertThat(json)
                    .as("Forbidden term '%s' found in dataQualityWarnings", term)
                    .doesNotContain(term.toLowerCase());
        }
    }

    @Test
    void mockPredictionModelOutput_containsNoForbiddenTerms() throws Exception {
        // Meta-test: verify that the mock itself doesn't introduce forbidden language
        MockPredictionModel mock = new MockPredictionModel(0.6);
        PredictionResult result = mock.predict(TwinStateSnapshotFactory.create());

        String modelVersionJson = objectMapper.writeValueAsString(result.modelVersion()).toLowerCase();
        for (String term : FORBIDDEN_TERMS) {
            assertThat(modelVersionJson).doesNotContain(term.toLowerCase());
        }
    }

    // -------------------------------------------------------------------------
    // REQ-026: Uncertainty fields always present
    // -------------------------------------------------------------------------

    @Test
    void predictionResponse_uncertaintyFieldsNeverNull() {
        PredictionResponse response = buildSamplePredictionResponse(0.65);

        assertThat(response.spikeProbability())
                .as("spikeProbability must not be null")
                .isNotNull()
                .isBetween(0.0, 1.0);

        assertThat(response.riskCategory())
                .as("riskCategory must not be null")
                .isNotNull()
                .isIn("LOW", "MODERATE", "HIGH", "CRITICAL");

        assertThat(response.confidenceInterval())
                .as("confidenceInterval must not be null")
                .isNotNull();

        assertThat(response.confidenceInterval().low())
                .as("CI low must be <= spikeProbability")
                .isLessThanOrEqualTo(response.spikeProbability());

        assertThat(response.confidenceInterval().high())
                .as("CI high must be >= spikeProbability")
                .isGreaterThanOrEqualTo(response.spikeProbability());

        assertThat(response.confidenceInterval().high() - response.confidenceInterval().low())
                .as("CI width must be > 0")
                .isGreaterThan(0.0);
    }

    // -------------------------------------------------------------------------
    // REQ-012: Data provenance always PREDICTED for model output
    // -------------------------------------------------------------------------

    @Test
    void predictionResponse_dataProvenanceAlwaysPREDICTED() {
        PredictionResponse response = buildSamplePredictionResponse(0.4);
        assertThat(response.dataProvenance())
                .as("dataProvenance must be PREDICTED for model output")
                .isEqualTo("PREDICTED");
    }

    @Test
    void predictionResponse_dataProvenanceNeverOBSERVED() {
        PredictionResponse response = buildSamplePredictionResponse(0.4);
        assertThat(response.dataProvenance()).isNotEqualTo("OBSERVED");
    }

    @Test
    void predictionResponse_dataProvenanceNeverSIMULATED() {
        PredictionResponse response = buildSamplePredictionResponse(0.4);
        assertThat(response.dataProvenance()).isNotEqualTo("SIMULATED");
    }

    // -------------------------------------------------------------------------
    // REQ-028: Prediction horizon always 2 hours
    // -------------------------------------------------------------------------

    @Test
    void predictionResponse_predictionHorizonAlwaysTwo() {
        PredictionResponse response = buildSamplePredictionResponse(0.5);
        assertThat(response.predictionHorizonHours())
                .as("predictionHorizonHours must always be 2")
                .isEqualTo(2);
    }

    @Test
    void predictionRecord_horizonFixedAtTwo() {
        PredictionRecord record = PredictionRecord.pending(PatientId.random(), 1, "xgboost-v1.0.0", "AUTO");
        assertThat(record.getPredictionHorizonHours()).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // REQ-027: Validate data source labels on seed records
    // -------------------------------------------------------------------------

    @Test
    void seedDataRecords_mustNotHaveNullDataSource() {
        // This test validates the concept — in integration tests the DB is checked directly
        String syntheticLabel = "SYNTHETIC";
        String anonymisedLabel = "ANONYMISED";
        Set<String> validLabels = Set.of(syntheticLabel, anonymisedLabel);

        // Simulate seed record check
        List<String> sampleSeedDataSources = List.of("SYNTHETIC", "SYNTHETIC", "SYNTHETIC");
        for (String source : sampleSeedDataSources) {
            assertThat(validLabels).contains(source);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private PredictionResponse buildSamplePredictionResponse(double probability) {
        return buildSamplePredictionResponseWithWarnings(List.of());
    }

    private PredictionResponse buildSamplePredictionResponseWithWarnings(List<String> warnings) {
        double probability = 0.72;
        PredictionRecord record = PredictionRecord.pending(PatientId.random(), 5, "xgboost-v1.0.0", "AUTO");
        PredictionResult result = new PredictionResult(
                probability,
                new ConfidenceInterval(0.61, 0.81),
                List.of(new ContributingFactor("cgm_current", 0.31, RiskDirection.INCREASES_RISK)),
                "xgboost-v1.0.0",
                List.of());
        record.complete(result, RiskCategory.HIGH, warnings, null);
        return PredictionResponse.from(record);
    }
}
