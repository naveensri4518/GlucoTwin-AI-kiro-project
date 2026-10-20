import React from 'react'

interface SectionCardProps {
  title: string
  subtitle?: string
  badge?: React.ReactNode
  action?: React.ReactNode
  children: React.ReactNode
  className?: string
  contentClassName?: string
}

export function SectionCard({
  title,
  subtitle,
  badge,
  action,
  children,
  className = '',
  contentClassName = '',
}: SectionCardProps) {
  return (
    <section className={`card p-5 lg:p-6 ${className}`}>
      {/* Header */}
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3 border-b border-border-subtle/80 pb-3">
        <div className="flex items-center gap-2.5">
          <div>
            <div className="flex items-center gap-2">
              <h3 className="text-xs font-semibold uppercase tracking-wider text-text-secondary">
                {title}
              </h3>
              {badge}
            </div>
            {subtitle && (
              <p className="mt-0.5 text-[11px] text-text-muted">{subtitle}</p>
            )}
          </div>
        </div>
        {action && <div className="flex items-center gap-2">{action}</div>}
      </div>

      {/* Main Body */}
      <div className={contentClassName}>{children}</div>
    </section>
  )
}
