/**
 * Matches the backend PatientSummaryResponse record.
 * Only patientId and createdAt are currently returned by GET /api/v1/patients.
 * Do not add fields that do not exist in the API response.
 */
export interface PatientSummary {
  patientId: string
  createdAt: string // ISO-8601 UTC
}

/** Spring Page<T> envelope */
export interface Page<T> {
  content: T[]
  page: {
    size: number
    number: number
    totalElements: number
    totalPages: number
  }
}

export type PatientPage = Page<PatientSummary>
