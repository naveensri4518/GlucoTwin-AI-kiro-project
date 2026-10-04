/**
 * Matches the backend CgmReadingResponse exactly.
 * Source: GET /api/v1/patients/{id}/wearable-events/cgm-history
 * All readings are OBSERVED provenance — wearable sensor data, not predictions.
 */
export interface CgmReading {
  value: number
  timestamp: string       // ISO-8601 UTC
  dataProvenance: 'OBSERVED'
}
