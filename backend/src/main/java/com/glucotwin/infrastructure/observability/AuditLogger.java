package com.glucotwin.infrastructure.observability;

import com.glucotwin.domain.prediction.RiskCategory;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.shared.PredictionId;
import com.glucotwin.infrastructure.persistence.entity.PredictionAuditLogJpaEntity;
import com.glucotwin.infrastructure.persistence.repository.PredictionAuditLogJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogger {

    private final PredictionAuditLogJpaRepository auditLogRepo;

    public void logPredictionCompleted(PatientId patientId, PredictionId predictionId,
                                        RiskCategory riskCategory) {
        persist("PREDICTION_COMPLETED", patientId, predictionId,
                Map.of("riskCategory", riskCategory.name()));
    }

    public void logPredictionFailed(PatientId patientId, PredictionId predictionId, String reason) {
        persist("PREDICTION_FAILED", patientId, predictionId, Map.of("reason", reason));
    }

    private void persist(String eventType, PatientId patientId, PredictionId predictionId,
                         Map<String, Object> metadata) {
        try {
            var entity = new PredictionAuditLogJpaEntity();
            entity.setPatientIdRef(PatientIdRef.hash(patientId));
            entity.setPredictionId(predictionId != null ? predictionId.value() : null);
            entity.setEventType(eventType);
            entity.setTraceId(MDC.get("traceId"));
            auditLogRepo.save(entity);
        } catch (Exception e) {
            log.error("Failed to write audit log entry: {}", e.getMessage());
        }
    }
}
