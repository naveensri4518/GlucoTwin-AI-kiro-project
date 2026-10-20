interface LoadingSkeletonProps {
  rows?: number
  height?: string
  className?: string
}

export function LoadingSkeleton({
  rows = 3,
  height = 'h-16',
  className = '',
}: LoadingSkeletonProps) {
  return (
    <div className={`space-y-3 ${className}`} role="status" aria-label="Loading content">
      {Array.from({ length: rows }).map((_, i) => (
        <div
          key={i}
          className={`card animate-pulse bg-bg-elevated/60 ${height}`}
          aria-hidden="true"
        />
      ))}
      <span className="sr-only">Loading clinical data…</span>
    </div>
  )
}
