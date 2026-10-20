import React from 'react'

interface EmptyStateProps {
  title: string
  description?: string
  icon?: React.ReactNode
  action?: React.ReactNode
  className?: string
}

export function EmptyState({
  title,
  description,
  icon,
  action,
  className = '',
}: EmptyStateProps) {
  return (
    <div
      className={`card flex flex-col items-center justify-center p-8 text-center border-dashed border-border/80 ${className}`}
    >
      {icon && (
        <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-bg-elevated text-text-muted border border-border-subtle">
          {icon}
        </div>
      )}
      <h4 className="text-sm font-semibold text-text-primary">{title}</h4>
      {description && (
        <p className="mt-1 max-w-sm text-xs text-text-secondary leading-relaxed">
          {description}
        </p>
      )}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}
