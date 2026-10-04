package com.glucotwin.application.insight;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.glucotwin.domain.insight.ClinicalInsightResponse;
import com.glucotwin.domain.insight.InsightAuditRecord;
import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.infrastructure.observability.PatientIdRef;
import com.glucotwin.infrastructure.persistence.entity.PredictionAuditLogJpaEntity;
import com.glucotwin.infrastructure.persistence.repository.PredictionAuditLogJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes a durable audit record to {@code prediction_audit_log} after a successful
 * clinical insight generation.
 *
 * <p>Transaction strategy: uses {@code REQUIRES_NEW} so the audit write runs in its
 * own independent transaction, isolated from the read-only clinical insight pipeline.
 * This prevents the audit write from converting the parent {@code readOnly=true}
 * transaction into a read-write transaction.
 *
 * <p>Audit failure is NON-FATAL:
 * <ul>
 *   <li>Exceptions are caught, logged at ERROR level, and swallowed.
 *   <li>The clinical insight response is never blocked or modified by audit failure.
 *   <li>HTTP 500 is never returned because the audit persistence failed.
 * </ul>
 *
 * <p>Security requirement: The raw {@code patientId} UUID is NEVER persisted.
 * {@code patientIdRef} is always {@code SHA-256(patientId)} via {@link PatientIdRef#hash(PatientId)}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InsightAuditWriter {

    private final PredictionAuditLogJpaRepository auditLogRepo;
    private final ObjectMapper objectMapper;

    /**
     * Build and persist an {@link InsightAuditRecord} from the generated insight.
     *
     * <p>This method must be called AFTER the {@link ClinicalInsightResponse} has been
     * successfully constructed. It must NOT be called if insight generation failed.
     *
     * <p>Runs in a separate {@code REQUIRES_NEW} transaction — never joins the parent
     * read-only transaction.
     *
     * @param patientId the patient whose insight was generated
     * @param insight   the successfully generated clinical insight
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(PatientId patientId, ClinicalInsightResponse insight) {
        try {
            InsightAuditRecord record = buildRecord(patientId, insight);
            PredictionAuditLogJpaEntity entity = toEntity(record);
            auditLogRepo.save(entity);
            log.debug("[InsightAuditWriter] Audit record written — eventType={} risk={}",
                    record.eventType(), record.riskCategory());
        } catch (Exception ex) {
            // Audit failure must never block the clinical insight response.
            log.error("[InsightAuditWriter] Failed to write audit record for patient ref={} — {}",
                    PatientIdRef.hash(patientId), ex.getMessage(), ex);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private InsightAuditRecord buildRecord(PatientId patientId, ClinicalInsightResponse insight) {
        return new InsightAuditRecord(
                PatientIdRef.hash(patientId),
                insight.latestPredictionId(),
                InsightAuditRecord.EVENT_TYPE,
                resolveActorRole(),
                MDC.get("traceId"),
                insight.riskCategory(),
                insight.spikeProbability(),
                insight.twinStateVersion(),
                insight.clinicalKnowledgeEvidence().size(),
                insight.dataProvenance(),
                insight.generatedAt());
    }

    private PredictionAuditLogJpaEntity toEntity(InsightAuditRecord record) {
        PredictionAuditLogJpaEntity entity = new PredictionAuditLogJpaEntity();
        entity.setPatientIdRef(record.patientIdRef());
        entity.setPredictionId(record.latestPredictionId());
        entity.setEventType(record.eventType());
        entity.setActorRole(record.actorRole());
        entity.setTraceId(record.traceId());
        entity.setMetadata(serializeMetadata(record));
        return entity;
    }

    private String serializeMetadata(InsightAuditRecord record) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("riskCategory",      record.riskCategory().name());
        meta.put("spikeProbability",  record.spikeProbability());
        meta.put("twinStateVersion",  record.twinStateVersion());
        meta.put("knowledgeItemCount", record.knowledgeItemCount());
        meta.put("dataProvenance",    record.dataProvenance());
        meta.put("generatedAt",       record.generatedAt().toString());
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException ex) {
            log.warn("[InsightAuditWriter] Failed to serialise audit metadata — {}",
                    ex.getMessage());
            return "{}";
        }
    }

    /**
     * Extract the highest-privilege role from the Spring Security context.
     * Returns null gracefully if no authentication context is available.
     */
    private String resolveActorRole() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) return null;
            return auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(a -> a.startsWith("ROLE_"))
                    .map(a -> a.substring(5)) // strip ROLE_ prefix
                    .findFirst()
                    .orElse(null);
        } catch (Exception ex) {
            log.debug("[InsightAuditWriter] Could not resolve actor role: {}", ex.getMessage());
            return null;
        }
    }
}
