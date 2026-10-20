import type { TwinState } from '@/types/digitalTwin'
import type { RiskCategory, Prediction } from '@/types/prediction'

interface StatusBadgeProps {
  status: TwinState['status'] | Prediction['status'] | RiskCategory | string
  type?: 'twin' | 'prediction' | 'risk' | 'health' | 'custom'
  className?: string
}

export function StatusBadge({ status, className = '' }: StatusBadgeProps) {
  const norm = String(status).toUpperCase()

  let colorClasses = 'bg-bg-subtle text-text-secondary border-border'
  let dotColor = 'bg-slate-400'

  // Risk Categories
  if (norm === 'LOW') {
    colorClasses = 'bg-status-success/10 text-status-success border-status-success/30'
    dotColor = 'bg-status-success'
  } else if (norm === 'MODERATE') {
    colorClasses = 'bg-status-warning/10 text-status-warning border-status-warning/30'
    dotColor = 'bg-status-warning'
  } else if (norm === 'HIGH') {
    colorClasses = 'bg-status-danger/10 text-status-danger border-status-danger/30'
    dotColor = 'bg-status-danger'
  } else if (norm === 'CRITICAL') {
    colorClasses = 'bg-status-danger/20 text-status-danger border-status-danger/50 font-bold'
    dotColor = 'bg-status-danger animate-pulse'
  }
  // Twin State Status
  else if (norm === 'ACTIVE' || norm === 'UP' || norm === 'COMPLETED' || norm === 'HEALTHY') {
    colorClasses = 'bg-status-success/10 text-status-success border-status-success/30'
    dotColor = 'bg-status-success'
  } else if (norm === 'STALE' || norm === 'PENDING' || norm === 'DEGRADED') {
    colorClasses = 'bg-status-warning/10 text-status-warning border-status-warning/30'
    dotColor = 'bg-status-warning'
  } else if (norm === 'INITIALISED' || norm === 'STANDBY') {
    colorClasses = 'bg-accent/10 text-accent border-accent/30'
    dotColor = 'bg-accent'
  } else if (norm === 'ARCHIVED' || norm === 'FAILED' || norm === 'DOWN') {
    colorClasses = 'bg-status-danger/10 text-status-danger border-status-danger/30'
    dotColor = 'bg-status-danger'
  }

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium tracking-wide ${colorClasses} ${className}`}
    >
      <span className={`h-1.5 w-1.5 rounded-full ${dotColor}`} aria-hidden="true" />
      {norm}
    </span>
  )
}
