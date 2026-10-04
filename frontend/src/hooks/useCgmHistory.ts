import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/apiClient'
import type { CgmReading } from '@/types/cgm'

/**
 * Fetches the rolling CGM history buffer from the Digital Twin.
 * Typically ≤12 readings (configurable on the backend).
 * staleTime is short — same order as twin-state polling — but we do not
 * poll independently; the existing twin-state refetch interval is sufficient.
 */
export function useCgmHistory(patientId: string | undefined) {
  return useQuery<CgmReading[]>({
    queryKey: ['patients', patientId, 'cgm-history'],
    queryFn: () =>
      api.get<CgmReading[]>(
        `/api/v1/patients/${patientId}/wearable-events/cgm-history`
      ),
    enabled: Boolean(patientId),
    staleTime: 15_000,
  })
}
