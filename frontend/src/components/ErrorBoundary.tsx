import { Component, type ErrorInfo, type ReactNode } from 'react'

interface Props {
  children: ReactNode
  fallback?: ReactNode
}

interface State {
  hasError: boolean
  error: Error | null
}

export class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null,
  }

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error }
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('GlucoTwin UI Uncaught error:', error, errorInfo)
  }

  private handleReset = () => {
    this.setState({ hasError: false, error: null })
    window.location.reload()
  }

  public render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback
      }

      return (
        <div
          className="flex min-h-[400px] flex-col items-center justify-center p-8 text-center"
          role="alert"
          aria-live="assertive"
        >
          <div className="card max-w-lg p-8">
            <span className="text-3xl" aria-hidden="true">
              ⚠
            </span>
            <h2 className="mt-3 text-base font-semibold text-slate-100">
              Something went wrong
            </h2>
            <p className="mt-1 text-xs text-slate-400">
              An unexpected error occurred while rendering the clinical interface.
            </p>
            {this.state.error?.message && (
              <pre className="mt-3 overflow-x-auto rounded bg-surface p-3 text-left font-mono text-[11px] text-red-400">
                {this.state.error.message}
              </pre>
            )}
            <div className="mt-4 flex justify-center gap-3">
              <button
                onClick={this.handleReset}
                className="rounded-md bg-accent px-4 py-2 text-xs font-medium text-white transition-colors hover:bg-accent-hover focus:outline-none focus:ring-2 focus:ring-accent"
              >
                Reload Dashboard
              </button>
            </div>
          </div>
        </div>
      )
    }

    return this.props.children
  }
}
