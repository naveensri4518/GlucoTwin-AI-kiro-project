package com.glucotwin.application;

import com.glucotwin.domain.prediction.*;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.shared.PredictionId;
import com.glucotwin.domain.shared.ResourceNotFoundException;
import com.glucotwin.domain.twin.*;
import com.glucotwin.infrastructure.config.GlucoTwinProperties;
import com.glucotwin.infrastructure.observability.AuditLogger;
import com.glucotwin.infrastructure.observability.PatientIdRef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Triggers a glucose spike prediction for a patient.
 * Called by the Redis consumer (AUTO) or directly by the API (MANUAL).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TriggerPredictionUseCase {

    private final DigitalTwinRepository digitalTwinRepository;
    private final PredictionRepository predictionRepository;
    private final PredictionModelPort predictionModelPort;
    private final TwinStateEventPublisher twinStateEventPublisher;
    private final AuditLogger auditLogger;
    private final GlucoTwinProperties properties;

    @Transactional
    public PredictionId execute(PatientId patientId, String triggeredBy) {
        // Load twin state
        DigitalTwinState twin = digitalTwinRepository.findByPatientId(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("DigitalTwinState", patientId.toString()));

        // Guard: ARCHIVED twin cannot produce predictions
        if (twin.getStatus() == TwinStatus.ARCHIVED) {
            throw new TwinStateArchivedError(patientId.toString());
        }

        // Take immutable snapshot inside read transaction
        TwinStateSnapshot snapshot = twin.snapshot();

        // Guard: glucose reading is mandatory
        if (!snapshot.hasGlucoseReading()) {
            throw new GlucoseReadingRequiredException(patientId.toString());
        }

        String modelVersion = predictionModelPort.getModelVersion();

        // Create PENDING record
        PredictionRecord record = PredictionRecord.pending(patientId, snapshot.twinVersion(),
                modelVersion, triggeredBy);
        predictionRepository.save(record);

        log.info("PREDICTION_TRIGGERED patientId={} predictionId={} trigger={}",
                PatientIdRef.hash(patientId), record.getPredictionId(), triggeredBy);

        // Collect data quality warnings
        List<String> qualityWarnings = new ArrayList<>();
        if (twin.getStatus() == TwinStatus.STALE) {
            qualityWarnings.add("STALE_WEARABLE_DATA");
        }
        if (snapshot.dynamicLayer() != null) {
            qualityWarnings.addAll(snapshot.dynamicLayer().dataQualityWarnings());
        }

        try {
            // Call ML model
            PredictionResult result = predictionModelPort.predict(snapshot);

            // Derive risk category
            RiskThresholds thresholds = properties.getRiskThresholds();
            RiskCategory riskCategory = RiskCategoryDeriver.derive(result.spikeProbability(), thresholds);

            // Complete the record
            record.complete(result, riskCategory, qualityWarnings, null);
            predictionRepository.save(record);

            log.info("PREDICTION_COMPLETED patientId={} predictionId={} riskCategory={} probability={} modelVersion={}",
                    PatientIdRef.hash(patientId), record.getPredictionId(),
                    riskCategory, result.spikeProbability(), result.modelVersion());

            // Write audit log
            auditLogger.logPredictionCompleted(patientId, record.getPredictionId(), riskCategory);

            // Notify dashboard (SSE — no-op in v1)
            twinStateEventPublisher.publishUpdated(snapshot);

        } catch (PredictionServiceUnavailableException | PredictionTimeoutException ex) {
            record.fail(ex.getMessage());
            predictionRepository.save(record);
            log.error("PREDICTION_FAILED patientId={} predictionId={} reason={}",
                    PatientIdRef.hash(patientId), record.getPredictionId(), ex.getMessage());
            throw ex;
        }

        return record.getPredictionId();
    }
}
