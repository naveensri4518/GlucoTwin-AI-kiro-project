import { Outlet } from 'react-router-dom'
import { ErrorBoundary } from '../ErrorBoundary'
import Sidebar from './Sidebar'
import TopBar from './TopBar'
import { IconShield } from '@/components/common/Icons'

/**
 * Persistent application shell — sidebar + top bar + scrollable main content.
 * All authenticated pages render inside <Outlet />.
 */
export default function AppShell() {
  return (
    <div className="flex h-screen w-screen overflow-hidden bg-bg text-text-primary">
      {/* Sidebar Navigation */}
      <Sidebar />

      {/* Main Content Area */}
      <div className="flex flex-1 flex-col overflow-hidden min-w-0">
        <TopBar />

        <main
          className="flex-1 overflow-y-auto overflow-x-hidden p-4 sm:p-6 lg:p-8"
          role="main"
          aria-label="Clinical workspace main content"
        >
          <div className="mx-auto max-w-7xl space-y-6">
            {/* Regulatory Safety Disclaimer — persistent across all views */}
            <aside
              className="flex items-center gap-3 rounded-xl border border-status-warning/30 bg-status-warning/10 px-4 py-2.5 text-xs text-status-warning shadow-sm"
              role="status"
              aria-live="polite"
            >
              <IconShield size={16} className="flex-shrink-0 text-status-warning" />
              <p className="leading-snug">
                <strong className="font-semibold uppercase tracking-wider text-status-warning mr-1">
                  Clinical Decision Support Only:
                </strong>
                Not a standalone diagnostic or treatment authority. Digital Twin trajectories and spike predictions are probabilistic machine learning estimates. Independent medical judgment is required.
              </p>
            </aside>

            {/* View container */}
            <ErrorBoundary>
              <Outlet />
            </ErrorBoundary>
          </div>
        </main>
      </div>
    </div>
  )
}
