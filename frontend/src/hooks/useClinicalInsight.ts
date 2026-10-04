/**
 * React Query mutation hook for the Phase 8/9 Clinical Insights endpoint.
 *
 * POST /api/v1/patients/{patientId}/clinical-insights
 *
 * The endpoint is synchronous — returns 200 OK with the full ClinicalInsightResponse.
 * No polling required. The backend orchestrates TwinAnalysisAgent →
 * PredictionAnalysisAgent → RiskEvidenceAgent + knowledge retrieval.
 *
 * No clinical values are fabricated in this hook.
 */

import { useMutation } from '@tanstack/react-query'
import { api } from '@/lib/apiClient'
import type { ClinicalInsightRequest, ClinicalInsightResponse } from '@/types/clinicalInsight'

export function useGenerateClinicalInsight(patientId: string | undefined) {
  return useMutation<ClinicalInsightResponse, Error, ClinicalInsightRequest>({
    mutationFn: (body: ClinicalInsightRequest) => {
      if (!patientId) {
        return Promise.reject(new Error('Patient ID is required'))
      }
      return api.post<ClinicalInsightResponse>(
        `/api/v1/patients/${patientId}/clinical-insights`,
        body,
      )
    },
  })
}
