import { Fragment } from 'react'
import { useLocation, Link } from 'react-router-dom'
import { useSystemHealth } from '@/hooks/useSystemHealth'
import { IconRefresh } from '@/components/common/Icons'
import { useQueryClient } from '@tanstack/react-query'

export default function TopBar() {
  const { pathname } = useLocation()
  const healthQuery = useSystemHealth()
  const queryClient = useQueryClient()

  // Generate breadcrumb and page title
  let pageTitle = 'Dashboard'
  let breadcrumb: Array<{ label: string; to?: string }> = [{ label: 'GlucoTwin' }]

  if (pathname === '/' || pathname === '/dashboard') {
    pageTitle = 'Clinical Intelligence Dashboard'
    breadcrumb.push({ label: 'Dashboard' })
  } else if (pathname === '/patients') {
    pageTitle = 'Patient Registry'
    breadcrumb.push({ label: 'Patients' })
  } else if (pathname.startsWith('/patients/')) {
    const patientId = pathname.replace('/patients/', '')
    pageTitle = 'Digital Twin Workspace'
    breadcrumb.push({ label: 'Patients', to: '/patients' })
    breadcrumb.push({ label: patientId.split('-')[0] + '…' })
  } else if (pathname.startsWith('/workspace/')) {
    const section = pathname.replace('/workspace/', '')
    const formatted = section.charAt(0).toUpperCase() + section.slice(1)
    pageTitle = `${formatted} Workspace`
    breadcrumb.push({ label: 'Workspace' })
    breadcrumb.push({ label: formatted })
  } else if (pathname === '/audit') {
    pageTitle = 'Audit & System Telemetry'
    breadcrumb.push({ label: 'Audit & Telemetry' })
  } else if (pathname === '/settings') {
    pageTitle = 'System & Model Settings'
    breadcrumb.push({ label: 'Settings' })
  }

  const isHealthy = healthQuery.data?.status === 'UP'
  const isPending = healthQuery.isLoading

  function handleGlobalRefresh() {
    void queryClient.invalidateQueries()
  }

  return (
    <header className="flex h-16 flex-shrink-0 items-center justify-between border-b border-border bg-bg-card/70 backdrop-blur-md px-6 z-10">
      {/* Left: Breadcrumbs & Title */}
      <div className="flex flex-col justify-center">
        <nav className="flex items-center gap-1.5 text-[11px] text-text-muted" aria-label="Breadcrumb">
          {breadcrumb.map((crumb, idx) => {
            const isLast = idx === breadcrumb.length - 1
            return (
              <Fragment key={idx}>
                {idx > 0 && <span className="text-border-hover">/</span>}
                {crumb.to && !isLast ? (
                  <Link to={crumb.to} className="hover:text-text-primary transition-colors">
                    {crumb.label}
                  </Link>
                ) : (
                  <span className={isLast ? 'text-text-secondary font-medium' : ''}>
                    {crumb.label}
                  </span>
                )}
              </Fragment>
            )
          })}
        </nav>
        <h1 className="text-sm font-semibold tracking-tight text-text-primary mt-0.5">
          {pageTitle}
        </h1>
      </div>

      {/* Right: Actions, System Status, Role */}
      <div className="flex items-center gap-3">
        {/* Refresh button */}
        <button
          onClick={handleGlobalRefresh}
          className="flex h-8 w-8 items-center justify-center rounded-lg border border-border bg-bg-card text-text-muted hover:border-border-hover hover:text-text-primary focus:outline-none focus:ring-1 focus:ring-accent transition-colors"
          title="Refresh view data"
          aria-label="Refresh view data"
        >
          <IconRefresh size={14} className={healthQuery.isFetching ? 'animate-spin' : ''} />
        </button>

        {/* Backend & Connectivity Status */}
        <div
          className={`flex items-center gap-2 rounded-lg border px-2.5 py-1 text-xs font-mono transition-colors ${
            isPending
              ? 'border-border bg-bg-elevated text-text-muted'
              : isHealthy
              ? 'border-status-success/30 bg-status-success/10 text-status-success'
              : 'border-status-danger/30 bg-status-danger/10 text-status-danger'
          }`}
          title={
            isHealthy
              ? `Connected to ${healthQuery.data?.service} (Status: UP)`
              : 'Connecting to backend...'
          }
        >
          <span
            className={`h-2 w-2 rounded-full ${
              isPending
                ? 'bg-text-muted animate-pulse'
                : isHealthy
                ? 'bg-status-success animate-pulse'
                : 'bg-status-danger'
            }`}
          />
          <span className="hidden sm:inline font-semibold">
            {isPending ? 'CONNECTING' : isHealthy ? 'API ONLINE' : 'OFFLINE'}
          </span>
        </div>

        {/* User Role */}
        <div className="flex items-center gap-2 border-l border-border pl-3">
          <div className="flex flex-col text-right">
            <span className="text-xs font-semibold text-text-primary leading-tight">
              Dr. Clinician
            </span>
            <span className="text-[10px] font-mono text-accent">
              ATTENDING / CDS
            </span>
          </div>
          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-accent/15 border border-accent/40 text-xs font-bold text-accent">
            DC
          </div>
        </div>
      </div>
    </header>
  )
}
