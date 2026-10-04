import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/apiClient'
import type { CreatePredictionResponse, Prediction, PredictionPage } from '@/types/prediction'

// ── Query key helpers ─────────────────────────────────────────────────────────

export const predictionKeys = {
  list: (patientId: string) => ['patients', patientId, 'predictions'] as const,
  detail: (predictionId: string) => ['predictions', predictionId] as const,
}

// ── List: GET /api/v1/patients/{id}/predictions ───────────────────────────────

export function usePatientPredictions(patientId: string | undefined, size = 10) {
  return useQuery<PredictionPage>({
    queryKey: patientId ? predictionKeys.list(patientId) : ['predictions-disabled'],
    queryFn: () =>
      api.get<PredictionPage>(`/api/v1/patients/${patientId}/predictions?page=0&size=${size}`),
    enabled: Boolean(patientId),
    staleTime: 10_000,
  })
}

// ── Single: GET /api/v1/predictions/{predictionId} ────────────────────────────

export function usePrediction(predictionId: string | undefined) {
  return useQuery<Prediction>({
    queryKey: predictionId ? predictionKeys.detail(predictionId) : ['prediction-disabled'],
    queryFn: () => api.get<Prediction>(`/api/v1/predictions/${predictionId}`),
    enabled: Boolean(predictionId),
    staleTime: 5_000,
    // Poll PENDING predictions until they complete
    refetchInterval: (query) => {
      const data = query.state.data
      return data?.status === 'PENDING' ? 1_500 : false
    },
  })
}

// ── Create: POST /api/v1/patients/{id}/predictions ────────────────────────────

export function useCreatePrediction(patientId: string | undefined) {
  const queryClient = useQueryClient()

  return useMutation<CreatePredictionResponse>({
    mutationFn: () =>
      api.post<CreatePredictionResponse>(`/api/v1/patients/${patientId}/predictions`),
    onSuccess: () => {
      // Invalidate prediction list so history refreshes automatically
      if (patientId) {
        void queryClient.invalidateQueries({ queryKey: predictionKeys.list(patientId) })
      }
    },
  })
}
