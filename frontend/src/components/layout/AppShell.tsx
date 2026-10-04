import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import TopBar from './TopBar'

/**
 * Persistent application shell — sidebar + top bar + scrollable main content.
 * All authenticated pages render inside <Outlet />.
 */
export default function AppShell() {
  return (
    <div className="flex h-screen overflow-hidden bg-surface text-slate-100">
      <Sidebar />

      <div className="flex flex-1 flex-col overflow-hidden">
        <TopBar />

        <main
          className="flex-1 overflow-y-auto p-6"
          role="main"
          aria-label="Main content"
        >
          {/* Safety disclaimer — always visible as required by product.md */}
          <div
            className="mb-4 rounded border border-amber-500/30 bg-amber-500/10 px-4 py-2 text-xs text-amber-300"
            role="status"
            aria-live="polite"
          >
            <strong>Research &amp; Decision Support Only.</strong> Not a diagnostic system.
            All predictions are probabilistic estimates. Always apply clinical judgment.
          </div>

          <Outlet />
        </main>
      </div>
    </div>
  )
}
