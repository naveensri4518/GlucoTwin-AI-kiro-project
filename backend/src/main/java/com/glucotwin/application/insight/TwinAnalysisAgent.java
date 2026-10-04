package com.glucotwin.application.insight;

import com.glucotwin.application.GetDigitalTwinStateUseCase;
import com.glucotwin.domain.insight.AgentResult;
import com.glucotwin.domain.insight.ObservedSignal;
import com.glucotwin.domain.insight.TwinAnalysisResult;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.shared.ResourceNotFoundException;
import com.glucotwin.domain.twin.DynamicLayer;
import com.glucotwin.domain.twin.StaticLayer;
import com.glucotwin.domain.twin.DigitalTwinState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes the current Digital Twin state for a patient.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Read the Digital Twin using the existing {@link GetDigitalTwinStateUseCase} (read-only).
 *   <li>Extract key OBSERVED signals from dynamic and static layers.
 *   <li>Report data quality warnings.
 *   <li>Never mutate the Digital Twin.
 *   <li>Never produce PREDICTED or SIMULATED output.
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TwinAnalysisAgent {

    static final String AGENT_NAME = "TwinAnalysisAgent";

    private final GetDigitalTwinStateUseCase getDigitalTwinStateUseCase;

    /**
     * Analyse the Digital Twin for {@code patientId}.
     * Returns a {@link AgentResult.Success} with a {@link TwinAnalysisResult},
     * or a {@link AgentResult.Failure} if the twin is unavailable.
     *
     * <p>This method is READ-ONLY — it never modifies the Digital Twin.
     */
    public AgentResult<TwinAnalysisResult> analyse(PatientId patientId) {
        DigitalTwinState twin;
        try {
            twin = getDigitalTwinStateUseCase.execute(patientId);
        } catch (ResourceNotFoundException ex) {
            log.warn("[{}] Digital Twin not found for patient {}", AGENT_NAME, patientId.value());
            return AgentResult.failure(AGENT_NAME,
                    "Digital Twin not found for patient: " + patientId.value());
        } catch (Exception ex) {
            log.error("[{}] Unexpected error loading twin for patient {}", AGENT_NAME,
                    patientId.value(), ex);
            return AgentResult.failure(AGENT_NAME,
                    "Failed to load Digital Twin: " + ex.getMessage());
        }

        List<ObservedSignal> signals = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // ── Static layer signals (EHR-derived, OBSERVED) ─────────────────────
        StaticLayer sl = twin.getStaticLayer();
        boolean hasStatic = sl != null && sl.hba1c() != null;
        if (sl != null) {
            if (sl.hba1c() != null) {
                signals.add(ObservedSignal.of("hba1c",
                        String.format("%.1f", sl.hba1c()), "%"));
            }
            if (sl.bmi() != null) {
                signals.add(ObservedSignal.of("bmi",
                        String.format("%.1f", sl.bmi()), "kg/m²"));
            }
            if (sl.fastingGlucose() != null) {
                signals.add(ObservedSignal.of("fastingGlucose",
                        String.format("%.1f", sl.fastingGlucose()), "mmol/L"));
            }
        }

        // ── Dynamic layer signals (wearable-derived, OBSERVED) ───────────────
        DynamicLayer dl = twin.getDynamicLayer();
        boolean hasDynamic = dl != null;
        if (dl != null) {
            if (dl.glucoseReading() != null) {
                signals.add(ObservedSignal.of("currentGlucose",
                        String.format("%.1f", dl.glucoseReading()), "mmol/L"));
            }
            if (dl.heartRate() != null) {
                signals.add(ObservedSignal.of("heartRate",
                        String.format("%.0f", dl.heartRate()), "bpm"));
            }
            if (dl.hrv() != null) {
                signals.add(ObservedSignal.of("hrv",
                        String.format("%.0f", dl.hrv()), "ms"));
            }
            if (dl.sleepDuration() != null) {
                signals.add(ObservedSignal.of("sleepDuration",
                        String.format("%.1f", dl.sleepDuration()), "hrs"));
            }
            if (dl.stepCount() != null) {
                signals.add(ObservedSignal.of("stepCount",
                        String.valueOf(dl.stepCount()), "steps"));
            }
            if (dl.activityLevel() != null) {
                signals.add(ObservedSignal.of("activityLevel",
                        dl.activityLevel().name()));
            }
            if (!dl.cgmHistory().isEmpty()) {
                signals.add(ObservedSignal.of("cgmHistorySize",
                        String.valueOf(dl.cgmHistory().size()), "readings"));
            }
            if (dl.dataQualityWarnings() != null) {
                warnings.addAll(dl.dataQualityWarnings());
            }
        }

        if (signals.isEmpty()) {
            warnings.add("No observed signals available — twin may be INITIALISED with no wearable data yet.");
        }

        TwinAnalysisResult result = new TwinAnalysisResult(
                twin.getTwinVersion(),
                twin.getStatus(),
                signals,
                warnings,
                hasDynamic,
                hasStatic);

        log.debug("[{}] Completed analysis for patient {} — {} signals, {} warnings",
                AGENT_NAME, patientId.value(), signals.size(), warnings.size());
        return AgentResult.success(result);
    }
}
