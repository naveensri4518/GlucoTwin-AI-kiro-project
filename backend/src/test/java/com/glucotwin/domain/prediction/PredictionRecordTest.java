package com.glucotwin.domain.prediction;

import com.glucotwin.domain.shared.PatientId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class PredictionRecordTest {

    @Test
    void pending_createsRecordWithCorrectInitialState() {
        var record = PredictionRecord.pending(PatientId.random(), 5, "xgboost-v1.0.0", "AUTO");

        assertThat(record.getStatus()).isEqualTo(PredictionStatus.PENDING);
        assertThat(record.getPredictionHorizonHours()).isEqualTo(2);
        assertThat(record.getDataProvenance()).isEqualTo(com.glucotwin.domain.shared.DataProvenance.PREDICTED);
        assertThat(record.getPredictionId()).isNotNull();
        assertThat(record.getPredictedAt()).isNotNull();
    }

    @Test
    void complete_transitionsToCOMPLETED() {
        var record = PredictionRecord.pending(PatientId.random(), 3, "xgboost-v1.0.0", "MANUAL");
        var result = buildResult(0.75);

        record.complete(result, RiskCategory.HIGH, List.of(), null);

        assertThat(record.getStatus()).isEqualTo(PredictionStatus.COMPLETED);
        assertThat(record.getSpikeProbability()).isEqualTo(0.75);
        assertThat(record.getRiskCategory()).isEqualTo(RiskCategory.HIGH);
        assertThat(record.getConfidenceInterval()).isNotNull();
    }

    @Test
    void complete_mergesImputedFieldsIntoWarnings() {
        var record = PredictionRecord.pending(PatientId.random(), 3, "xgboost-v1.0.0", "AUTO");
        var result = new PredictionResult(0.5,
                new ConfidenceInterval(0.4, 0.6),
                List.of(), "xgboost-v1.0.0", List.of("hrv_current", "bmi"));

        record.complete(result, RiskCategory.MODERATE, List.of(), null);

        assertThat(record.getDataQualityWarnings())
                .contains("IMPUTED_FIELD:hrv_current", "IMPUTED_FIELD:bmi");
    }

    @Test
    void fail_transitionsToFAILED() {
        var record = PredictionRecord.pending(PatientId.random(), 2, "xgboost-v1.0.0", "AUTO");

        record.fail("ML service unavailable");

        assertThat(record.getStatus()).isEqualTo(PredictionStatus.FAILED);
        assertThat(record.getFailureReason()).isEqualTo("ML service unavailable");
    }

    @Test
    void completedRecord_rejectsFurtherMutation() {
        var record = PredictionRecord.pending(PatientId.random(), 1, "xgboost-v1.0.0", "AUTO");
        record.complete(buildResult(0.5), RiskCategory.MODERATE, List.of(), null);

        assertThatThrownBy(() -> record.complete(buildResult(0.8), RiskCategory.HIGH, List.of(), null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> record.fail("reason"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failedRecord_rejectsFurtherMutation() {
        var record = PredictionRecord.pending(PatientId.random(), 1, "xgboost-v1.0.0", "AUTO");
        record.fail("error");

        assertThatThrownBy(() -> record.fail("another error"))
                .isInstanceOf(IllegalStateException.class);
    }

    private PredictionResult buildResult(double probability) {
        return new PredictionResult(
                probability,
                new ConfidenceInterval(probability - 0.1, probability + 0.1),
                List.of(new ContributingFactor("cgm_current", 0.3, RiskDirection.INCREASES_RISK)),
                "xgboost-v1.0.0",
                List.of());
    }
}
