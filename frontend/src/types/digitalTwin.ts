/**
 * Types matching the exact backend API response shapes.
 * Fields reflect what the backend actually returns — do not add invented fields.
 *
 * EhrResponse  ← GET /api/v1/patients/{id}/ehr
 * TwinState    ← GET /api/v1/patients/{id}/twin-state
 */

/** GET /api/v1/patients/{id}/ehr */
export interface EhrRecord {
  ehrId: string
  patientId: string
  dateOfBirth: string        // ISO date "YYYY-MM-DD"
  sex: 'MALE' | 'FEMALE' | 'OTHER'
  bmi: number | null
  diabetesOnsetDate: string  // ISO date
  hba1c: number | null
  fastingGlucose: number | null
  dataProvenance: 'OBSERVED'
}

/** Dynamic wearable layer inside GET /api/v1/patients/{id}/twin-state */
export interface DynamicLayer {
  glucoseReading: number | null
  heartRate: number | null
  hrv: number | null
  sleepDuration: number | null
  sleepStage: 'AWAKE' | 'LIGHT' | 'DEEP' | 'REM' | null
  stepCount: number | null
  activityLevel: 'SEDENTARY' | 'LIGHT' | 'MODERATE' | 'VIGOROUS' | null
  eventTimestamp: string | null  // ISO-8601 UTC
  dataProvenance: 'OBSERVED'
  cgmHistorySize: number
  dataQualityWarnings: string[]
}

/** GET /api/v1/patients/{id}/twin-state */
export interface TwinState {
  patientId: string
  twinVersion: number
  status: 'INITIALISED' | 'ACTIVE' | 'STALE' | 'ARCHIVED'
  lastUpdatedAt: string   // ISO-8601 UTC
  createdAt: string       // ISO-8601 UTC
  dynamicLayer: DynamicLayer | null
}
