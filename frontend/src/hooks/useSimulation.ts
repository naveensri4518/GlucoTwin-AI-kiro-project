/**
 * React Query mutation hook for the Phase 7A What-If Simulation endpoint.
 *
 * POST /api/v1/patients/{patientId}/simulations
 *
 * The endpoint is synchronous — returns 200 OK with the full SimulationResponse.
 * No polling is required (unlike predictions which return 202 PENDING).
 *
 * The backend's XGBoost pipeline is the sole source of truth for simulation
 * results. No probability values are computed or fabricated in this hook.
 */

import { useMutation } from '@tanstack/react-query'
import { api } from '@/lib/apiClient'
import type { SimulationRequest, SimulationResponse } from '@/types/simulation'

export function useRunSimulation(patientId: string | undefined) {
  return useMutation<SimulationResponse, Error, SimulationRequest>({
    mutationFn: (body: SimulationRequest) => {
      if (!patientId) {
        return Promise.reject(new Error('Patient ID is required'))
      }
      return api.post<SimulationResponse>(
        `/api/v1/patients/${patientId}/simulations`,
        body,
      )
    },
  })
}
