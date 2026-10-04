/**
 * Types matching the backend PredictionResponse exactly.
 * Source: backend/src/main/java/com/glucotwin/api/dto/PredictionResponse.java
 * Do not add fields that do not exist in the API response.
 */

export type RiskCategory = 'LOW' | 'MODERATE' | 'HIGH' | 'CRITICAL'
export type PredictionStatus = 'PENDING' | 'COMPLETED' | 'FAILED'

export interface ConfidenceInterval {
  low: number
  high: number
}

export interface ContributingFactor {
  factorName: string
  contribution: number
  direction: 'INCREASES_RISK' | 'DECREASES_RISK'
}

/** Single prediction — GET /api/v1/predictions/{predictionId} */
export interface Prediction {
  predictionId: string
  patientId: string
  predictedAt: string           // ISO-8601 UTC
  predictionHorizonHours: number
  status: PredictionStatus
  spikeProbability: number | null
  riskCategory: RiskCategory | null
  confidenceInterval: ConfidenceInterval | null
  topContributingFactors: ContributingFactor[]
  dataProvenance: 'PREDICTED'
  twinStateVersion: number
  modelVersion: string
  dataQualityWarnings: string[]
  failureReason: string | null
}

/** Spring Page<Prediction> — GET /api/v1/patients/{id}/predictions */
export interface PredictionPage {
  content: Prediction[]
  page: {
    size: number
    number: number
    totalElements: number
    totalPages: number
  }
}

/** 202 body from POST /api/v1/patients/{id}/predictions */
export interface CreatePredictionResponse {
  predictionId: string
  status: 'PENDING'
  location: string
}
