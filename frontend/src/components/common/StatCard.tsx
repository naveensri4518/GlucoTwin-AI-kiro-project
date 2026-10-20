import React from 'react'
import { ProvenanceBadge, type ProvenanceType } from './ProvenanceBadge'

interface StatCardProps {
  label: string
  value: React.ReactNode
  unit?: string
  subtitle?: string
  icon?: React.ReactNode
  provenance?: ProvenanceType
  trend?: {
    direction: 'up' | 'down' | 'neutral'
    text: string
  }
  highlight?: boolean
  className?: string
  statusIndicator?: 'success' | 'warning' | 'danger' | 'info'
}

export function StatCard({
  label,
  value,
  unit,
  subtitle,
  icon,
  provenance,
  trend,
  highlight = false,
  className = '',
  statusIndicator,
}: StatCardProps) {
  return (
    <div
      className={`card p-5 relative overflow-hidden transition-all duration-200 hover:border-border-hover ${
        highlight ? 'border-accent/40 bg-gradient-to-b from-bg-card to-bg-elevated' : 'bg-bg-card'
      } ${className}`}
    >
      {/* Top row: Label & Provenance / Icon */}
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-medium uppercase tracking-wider text-text-secondary truncate">
          {label}
        </span>
        <div className="flex items-center gap-2 flex-shrink-0">
          {provenance && <ProvenanceBadge label={provenance} size="sm" />}
          {icon && (
            <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-bg-elevated text-accent border border-border-subtle">
              {icon}
            </div>
          )}
        </div>
      </div>

      {/* Metric Value */}
      <div className="mt-3 flex items-baseline gap-1.5">
        <span
          className={`text-2xl lg:text-3xl font-bold tracking-tight tabular-nums ${
            statusIndicator === 'danger'
              ? 'text-status-danger'
              : statusIndicator === 'warning'
              ? 'text-status-warning'
              : statusIndicator === 'success'
              ? 'text-status-success'
              : highlight
              ? 'text-accent'
              : 'text-text-primary'
          }`}
        >
          {value}
        </span>
        {unit && <span className="text-xs font-normal text-text-muted">{unit}</span>}
      </div>

      {/* Subtitle / Trend */}
      {(subtitle || trend) && (
        <div className="mt-2 flex items-center justify-between gap-2 text-[11px] text-text-secondary border-t border-border-subtle/80 pt-2">
          {subtitle && <span className="truncate">{subtitle}</span>}
          {trend && (
            <span
              className={`font-medium flex-shrink-0 ${
                trend.direction === 'up'
                  ? 'text-status-warning'
                  : trend.direction === 'down'
                  ? 'text-status-success'
                  : 'text-text-muted'
              }`}
            >
              {trend.text}
            </span>
          )}
        </div>
      )}
    </div>
  )
}
