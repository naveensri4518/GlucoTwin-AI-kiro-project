export type ProvenanceType = 'OBSERVED' | 'PREDICTED' | 'SIMULATED'

interface ProvenanceBadgeProps {
  label: ProvenanceType | string
  className?: string
  size?: 'sm' | 'md'
}

export function ProvenanceBadge({
  label,
  className = '',
  size = 'sm',
}: ProvenanceBadgeProps) {
  const norm = label.toUpperCase()

  let colorClasses = 'border-border text-text-secondary bg-bg-card'
  let labelText = norm

  if (norm === 'OBSERVED') {
    colorClasses = 'border-status-info/40 bg-status-info/10 text-status-info'
    labelText = 'OBSERVED'
  } else if (norm === 'PREDICTED') {
    colorClasses = 'border-accent/40 bg-accent/10 text-accent font-medium'
    labelText = 'PREDICTED'
  } else if (norm === 'SIMULATED') {
    colorClasses = 'border-status-warning/40 bg-status-warning/10 text-status-warning font-semibold tracking-wider'
    labelText = 'SIMULATED'
  }

  const sizeClass = size === 'sm' ? 'text-[10px] px-2 py-0.5' : 'text-xs px-2.5 py-1'

  return (
    <span
      className={`inline-flex items-center gap-1 rounded-md border uppercase font-mono tracking-wide ${sizeClass} ${colorClasses} ${className}`}
      title={
        norm === 'OBSERVED'
          ? 'Observed clinical EHR or verified wearable telemetry'
          : norm === 'PREDICTED'
          ? 'Probabilistic model output — not diagnostic'
          : 'Hypothetical what-if counterfactual scenario'
      }
    >
      <span className="h-1.5 w-1.5 rounded-full bg-current opacity-80" aria-hidden="true" />
      {labelText}
    </span>
  )
}
