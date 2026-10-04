/**
 * Types matching the backend ClinicalInsightResponseDto exactly.
 * Source: backend/src/main/java/com/glucotwin/api/dto/ClinicalInsightResponseDto.java
 *
 * Do not add fields that do not exist in the API response.
 * Do not fabricate medical data.
 * Provenance labels are preserved exactly as returned by the backend.
 */

import type { RiskCategory } from '@/types/prediction'

// ── Request ───────────────────────────────────────────────────────────────────

/** POST /api/v1/patients/{patientId}/clinical-insights */
export interface ClinicalInsightRequest {
  /** Optional clinician question, max 500 chars. Does not alter data retrieval. */
  question?: string
}

// ── Response ──────────────────────────────────────────────────────────────────

export interface ClinicalInsightConfidenceInterval {
  low: number
  high: number
}

/** OBSERVED provenance — wearable/EHR data only. */
export interface ObservedSignal {
  name: string
  value: string
  unit: string | null
  provenance: 'OBSERVED'
}

/** PREDICTED provenance — model output only. */
export interface InsightContributingFactor {
  factorName: string
  contribution: number
  direction: 'INCREASES_RISK' | 'DECREASES_RISK'
}

/**
 * CLINICAL_KNOWLEDGE provenance — general educational context from the
 * local knowledge corpus. Never patient-specific data.
 */
export interface ClinicalKnowledgeEvidence {
  knowledgeId: string
  title: string
  sourceName: string
  sourceReference: string | null
  version: string
  topic: string
  excerpt: string
  provenance: 'CLINICAL_KNOWLEDGE'
}

// ── Phase 12: Execution Trace ─────────────────────────────────────────────────

/** Single pipeline stage trace — observability metadata only, not clinical evidence. */
export interface AgentStepTrace {
  agentName: string
  /** SUCCESS | FAILURE | SKIPPED */
  status: 'SUCCESS' | 'FAILURE' | 'SKIPPED'
  durationMs: number
  detail: string
}

/** Full pipeline execution trace — observability metadata only. */
export interface AgentExecutionTrace {
  /** MDC traceId, may be null if no trace context was active. */
  traceId: string | null
  startedAt: string   // ISO-8601 UTC
  /** Clinical pipeline duration in ms (excludes audit write time). */
  totalDurationMs: number
  steps: AgentStepTrace[]
}

/**
 * Full response from POST /api/v1/patients/{patientId}/clinical-insights.
 *
 * Provenance breakdown:
 *   keyObservedSignals        → OBSERVED
 *   spikeProbability/riskCategory/confidenceInterval → PREDICTED
 *   clinicalKnowledgeEvidence → CLINICAL_KNOWLEDGE
 *   dataProvenance            → "OBSERVED+PREDICTED"
 *   safetyDisclaimer          → always present
 *   executionTrace            → observability only, may be absent
 *
 * SIMULATED data is never included in this response.
 */
export interface ClinicalInsightResponse {
  patientId: string
  generatedAt: string              // ISO-8601 UTC
  twinStateVersion: number
  latestPredictionId: string
  riskCategory: RiskCategory
  spikeProbability: number
  confidenceInterval: ClinicalInsightConfidenceInterval
  keyObservedSignals: ObservedSignal[]
  contributingFactors: InsightContributingFactor[]
  dataQualityWarnings: string[]
  evidenceSummary: string
  uncertainty: string
  dataProvenance: string           // always "OBSERVED+PREDICTED"
  safetyDisclaimer: string         // always the canonical safety text
  clinicalKnowledgeEvidence: ClinicalKnowledgeEvidence[]
  /** Phase 12: pipeline execution trace. Absent when backend did not capture it. */
  executionTrace?: AgentExecutionTrace | null
}
