/**
 * Types matching the backend SimulationResponse exactly.
 * Source: backend/src/main/java/com/glucotwin/api/dto/SimulationResponse.java
 *
 * dataProvenance is always "SIMULATED" — enforced in the domain.
 * deltaVsBaseline is @JsonInclude NON_NULL — it may be absent from the JSON response.
 *
 * Do not add fields that do not exist in the API response.
 * Do not compute or fabricate simulation values in the frontend.
 */

import type { RiskCategory } from '@/types/prediction'

// ── Request ───────────────────────────────────────────────────────────────────

export type ActivityLevel = 'SEDENTARY' | 'LIGHT' | 'MODERATE' | 'VIGOROUS'

/** POST /api/v1/patients/{patientId}/simulations */
export interface SimulationRequest {
  /** Optional. 0–500 g. Backend rejects values outside this range. */
  mealCarbsGrams?: number | null
  /** Optional. One of SEDENTARY | LIGHT | MODERATE | VIGOROUS. */
  activityLevel?: ActivityLevel | null
  /** Optional. */
  medicationTaken?: boolean | null
}

// ── Response ──────────────────────────────────────────────────────────────────

export interface SimulationConfidenceInterval {
  low: number
  high: number
}

export interface SimulationContributingFactor {
  factorName: string
  contribution: number
  direction: 'INCREASES_RISK' | 'DECREASES_RISK'
}

/**
 * Full response from POST /api/v1/patients/{patientId}/simulations.
 * The endpoint is synchronous — returns 200 OK with the complete result.
 */
export interface SimulationResponse {
  simulationId: string
  patientId: string
  /** ISO-8601 UTC timestamp */
  simulatedAt: string
  predictionHorizonHours: number
  /** 0.0–1.0 probability of a glucose spike */
  spikeProbability: number
  riskCategory: RiskCategory
  confidenceInterval: SimulationConfidenceInterval
  topContributingFactors: SimulationContributingFactor[]
  /** Always "SIMULATED" — enforced in the backend domain. */
  dataProvenance: 'SIMULATED'
  twinStateVersion: number
  modelVersion: string
  /** Echoes back the non-null scenario inputs that were submitted. */
  scenarioInputs: Record<string, unknown>
  /**
   * Change in spike probability vs the unmodified baseline prediction.
   * Absent (@JsonInclude NON_NULL) when no baseline prediction exists.
   * Display "Baseline unavailable" rather than 0 or a fabricated value.
   */
  deltaVsBaseline?: number | null
  dataQualityWarnings: string[]
  /** Backend-provided safety disclaimer text — always display this verbatim. */
  disclaimer: string
}

// ── Form state ────────────────────────────────────────────────────────────────

/** Internal form state for the What-If Simulation panel. */
export interface SimulationFormState {
  mealCarbsGrams: string          // string for controlled input, parsed before submission
  activityLevel: ActivityLevel | ''
  medicationTaken: boolean
}

export const SIMULATION_FORM_DEFAULTS: SimulationFormState = {
  mealCarbsGrams: '',
  activityLevel: '',
  medicationTaken: false,
}

export const ACTIVITY_LEVEL_OPTIONS: { value: ActivityLevel; label: string }[] = [
  { value: 'SEDENTARY', label: 'Sedentary' },
  { value: 'LIGHT',     label: 'Light' },
  { value: 'MODERATE',  label: 'Moderate' },
  { value: 'VIGOROUS',  label: 'Vigorous' },
]
