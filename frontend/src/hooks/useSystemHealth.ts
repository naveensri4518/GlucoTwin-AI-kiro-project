import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/apiClient'

export interface HealthResponse {
  status: 'UP' | 'DOWN' | 'DEGRADED' | string
  service: string
  timestamp: string
}

export function useSystemHealth() {
  return useQuery<HealthResponse>({
    queryKey: ['system-health'],
    queryFn: async () => {
      return api.get<HealthResponse>('/api/health')
    },
    refetchInterval: 30_000, // check every 30 seconds
    retry: 2,
    staleTime: 10_000,
  })
}
