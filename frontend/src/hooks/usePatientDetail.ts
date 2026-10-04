import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/apiClient'
import type { EhrRecord, TwinState } from '@/types/digitalTwin'

export function usePatientEhr(patientId: string | undefined) {
  return useQuery<EhrRecord>({
    queryKey: ['patients', patientId, 'ehr'],
    queryFn: () => api.get<EhrRecord>(`/api/v1/patients/${patientId}/ehr`),
    enabled: Boolean(patientId),
    staleTime: 60_000,  // EHR is slow-changing — cache for 1 min
  })
}

export function usePatientTwinState(patientId: string | undefined) {
  return useQuery<TwinState>({
    queryKey: ['patients', patientId, 'twin-state'],
    queryFn: () => api.get<TwinState>(`/api/v1/patients/${patientId}/twin-state`),
    enabled: Boolean(patientId),
    staleTime: 10_000,  // Twin state changes on every wearable event — fresher cache
    refetchInterval: 30_000,  // poll every 30 s while the page is open
  })
}
