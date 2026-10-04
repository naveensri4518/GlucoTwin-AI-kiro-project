import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/apiClient'
import type { PatientPage } from '@/types/patient'

const PATIENTS_QUERY_KEY = ['patients'] as const

/**
 * Fetches the paginated patient list from GET /api/v1/patients.
 * page and size default to 0 / 20 — matching backend defaults.
 */
export function usePatients(page = 0, size = 20) {
  return useQuery<PatientPage>({
    queryKey: [...PATIENTS_QUERY_KEY, page, size],
    queryFn: () => api.get<PatientPage>(`/api/v1/patients?page=${page}&size=${size}`),
  })
}

export { PATIENTS_QUERY_KEY }
