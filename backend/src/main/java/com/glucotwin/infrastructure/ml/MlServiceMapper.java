package com.glucotwin.infrastructure.ml;

import com.glucotwin.domain.prediction.*;
import com.glucotwin.domain.twin.*;

import java.util.*;

/** Maps between domain TwinStateSnapshot and ML service DTOs. */
public final class MlServiceMapper {

    private MlServiceMapper() {}

    public static MlPredictionRequest toRequest(TwinStateSnapshot snapshot) {
        Map<String, Object> staticMap = new LinkedHashMap<>();
        if (snapshot.staticLayer() != null) {
            StaticLayer sl = snapshot.staticLayer();
            staticMap.put("bmi", sl.bmi());
            staticMap.put("hba1c", sl.hba1c());
            staticMap.put("fasting_glucose", sl.fastingGlucose());
            staticMap.put("date_of_birth", sl.dateOfBirth() != null ? sl.dateOfBirth().toString() : null);
            staticMap.put("sex", sl.sex() != null ? sl.sex().name() : null);
            staticMap.put("diabetes_onset_date", sl.diabetesOnsetDate() != null ? sl.diabetesOnsetDate().toString() : null);
        }

        Map<String, Object> dynMap = new LinkedHashMap<>();
        if (snapshot.dynamicLayer() != null) {
            DynamicLayer dl = snapshot.dynamicLayer();
            dynMap.put("glucose_reading", dl.glucoseReading());
            dynMap.put("heart_rate", dl.heartRate());
            dynMap.put("hrv", dl.hrv());
            dynMap.put("sleep_duration", dl.sleepDuration());
            dynMap.put("sleep_stage", dl.sleepStage() != null ? dl.sleepStage().name() : null);
            dynMap.put("step_count", dl.stepCount());
            dynMap.put("activity_level", dl.activityLevel() != null ? dl.activityLevel().name() : null);
            dynMap.put("event_timestamp", dl.eventTimestamp() != null ? dl.eventTimestamp().toString() : null);
            // CGM history
            List<Map<String, Object>> cgmList = new ArrayList<>();
            for (CgmReading r : dl.cgmHistory()) {
                cgmList.add(Map.of("value", r.value(), "timestamp", r.timestamp().toString()));
            }
            dynMap.put("cgm_history", cgmList);
        }

        return new MlPredictionRequest(
                snapshot.patientId().value().toString(),
                snapshot.twinVersion(),
                snapshot.status().name(),
                staticMap,
                dynMap,
                snapshot.snapshotTakenAt());
    }

    public static PredictionResult toResult(MlPredictionResponse response) {
        double prob = Math.max(0.0, Math.min(1.0, response.spikeProbability()));

        Map<String, Double> ci = response.confidenceInterval();
        double ciLow = ci != null ? ci.getOrDefault("low", Math.max(0.0, prob - 0.15)) : Math.max(0.0, prob - 0.15);
        double ciHigh = ci != null ? ci.getOrDefault("high", Math.min(1.0, prob + 0.15)) : Math.min(1.0, prob + 0.15);

        // Ensure non-degenerate interval
        if (ciLow >= ciHigh) {
            ciLow = Math.max(0.0, prob - 0.01);
            ciHigh = Math.min(1.0, prob + 0.01);
        }
        ConfidenceInterval confidenceInterval = new ConfidenceInterval(ciLow, ciHigh);

        List<ContributingFactor> factors = new ArrayList<>();
        if (response.topContributingFactors() != null) {
            for (Map<String, Object> m : response.topContributingFactors()) {
                String dir = (String) m.getOrDefault("direction", "INCREASES_RISK");
                factors.add(new ContributingFactor(
                        (String) m.get("factor_name"),
                        ((Number) m.getOrDefault("contribution", 0.0)).doubleValue(),
                        RiskDirection.valueOf(dir)));
            }
        }

        return new PredictionResult(
                prob,
                confidenceInterval,
                factors,
                response.modelVersion() != null ? response.modelVersion() : "unknown",
                response.imputedFields() != null ? response.imputedFields() : List.of());
    }
}
