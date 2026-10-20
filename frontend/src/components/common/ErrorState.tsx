import { IconAlertTriangle, IconRefresh } from './Icons'

interface ErrorStateProps {
  title?: string
  message: string
  onRetry?: () => void
  className?: string
}

export function ErrorState({
  title = 'Service Unavailable',
  message,
  onRetry,
  className = '',
}: ErrorStateProps) {
  return (
    <div
      className={`card flex flex-col items-center justify-center p-8 text-center border-status-danger/30 bg-status-danger/5 ${className}`}
      role="alert"
      aria-live="assertive"
    >
      <div className="flex h-10 w-10 items-center justify-center rounded-full bg-status-danger/15 text-status-danger mb-3 border border-status-danger/25">
        <IconAlertTriangle size={20} />
      </div>
      <h4 className="text-sm font-semibold text-text-primary">{title}</h4>
      <p className="mt-1 max-w-md text-xs text-text-secondary leading-relaxed font-mono">
        {message}
      </p>
      {onRetry && (
        <button
          onClick={onRetry}
          className="btn-primary mt-4 text-xs"
        >
          <IconRefresh size={14} />
          Retry Request
        </button>
      )}
    </div>
  )
}
