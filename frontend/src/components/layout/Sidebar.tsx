import React, { useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import {
  IconDashboard,
  IconUsers,
  IconBrain,
  IconSparkles,
  IconTwin,
  IconHeartPulse,
  IconSliders,
  IconShield,
  IconSettings,
  IconChevronLeft,
  IconChevronRight,
  IconLogo,
} from '@/components/common/Icons'

interface NavItem {
  id: string
  label: string
  to: string
  icon: (props: { size?: number; className?: string }) => React.ReactElement
  badge?: string
  description: string
}

export const NAV_ITEMS: NavItem[] = [
  {
    id: 'dashboard',
    label: 'Dashboard',
    to: '/dashboard',
    icon: IconDashboard,
    description: 'Clinical overview & cohort health',
  },
  {
    id: 'patients',
    label: 'Patients',
    to: '/patients',
    icon: IconUsers,
    description: 'Patient registry & digital twins',
  },
  {
    id: 'predictions',
    label: 'Predictions',
    to: '/predictions',
    icon: IconBrain,
    description: '2-Hour glucose spike risk forecasts',
  },
  {
    id: 'insights',
    label: 'Clinical Insights',
    to: '/insights',
    icon: IconSparkles,
    description: 'Evidence summaries & agent reasoning',
  },
  {
    id: 'twin',
    label: 'Digital Twin',
    to: '/twin',
    icon: IconTwin,
    description: 'Physiological twin state & parameters',
  },
  {
    id: 'wearables',
    label: 'Wearable Monitoring',
    to: '/wearables',
    icon: IconHeartPulse,
    description: 'CGM telemetry, HR & activity streams',
  },
  {
    id: 'simulations',
    label: 'Simulations',
    to: '/simulations',
    icon: IconSliders,
    description: 'What-if counterfactual scenario modeling',
  },
  {
    id: 'audit',
    label: 'Audit & Activity',
    to: '/audit',
    icon: IconShield,
    description: 'System telemetry & data provenance log',
  },
  {
    id: 'settings',
    label: 'Settings',
    to: '/settings',
    icon: IconSettings,
    description: 'Model configuration & system credentials',
  },
]

export default function Sidebar() {
  const [collapsed, setCollapsed] = useState(false)
  const location = useLocation()

  return (
    <aside
      className={`flex flex-col border-r border-border bg-bg-sidebar transition-all duration-300 z-20 flex-shrink-0 ${
        collapsed ? 'w-18' : 'w-64'
      }`}
      aria-label="Clinical platform navigation"
    >
      {/* Brand Header */}
      <div className="flex h-16 items-center justify-between border-b border-border px-4">
        <div className="flex items-center gap-3 overflow-hidden">
          <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg bg-bg-card border border-border">
            <IconLogo size={22} />
          </div>
          {!collapsed && (
            <div className="min-w-0 transition-opacity duration-200">
              <div className="flex items-center gap-1.5">
                <span className="font-semibold text-sm tracking-tight text-text-primary">
                  GlucoTwin
                </span>
                <span className="rounded bg-accent/15 px-1 py-0.2 text-[9px] font-mono font-semibold text-accent border border-accent/30">
                  AI
                </span>
              </div>
              <p className="text-[10px] uppercase tracking-wider text-text-secondary font-medium">
                Clinical Intelligence
              </p>
            </div>
          )}
        </div>

        {/* Collapse toggle button */}
        <button
          onClick={() => setCollapsed(!collapsed)}
          className="flex h-7 w-7 items-center justify-center rounded-md border border-border-subtle bg-bg-card text-text-muted hover:border-border hover:text-text-primary focus:outline-none focus:ring-1 focus:ring-accent transition-colors"
          title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        >
          {collapsed ? <IconChevronRight size={14} /> : <IconChevronLeft size={14} />}
        </button>
      </div>

      {/* Navigation List */}
      <nav className="flex-1 overflow-y-auto px-2.5 py-4 space-y-1">
        {NAV_ITEMS.map((item) => {
          const Icon = item.icon
          const isItemActive =
            location.pathname === item.to ||
            (item.to === '/patients' && location.pathname.startsWith('/patients')) ||
            (item.to === '/dashboard' && (location.pathname === '/' || location.pathname === '/dashboard'))

          return (
            <NavLink
              key={item.id}
              to={item.to}
              title={collapsed ? `${item.label} — ${item.description}` : undefined}
              className={({ isActive }) =>
                [
                  'group flex items-center gap-3 rounded-lg px-3 py-2 text-xs font-medium transition-all duration-150 relative',
                  isActive || isItemActive
                    ? 'bg-accent/15 text-accent font-semibold border border-accent/30 shadow-sm'
                    : 'text-text-secondary hover:bg-bg-elevated hover:text-text-primary border border-transparent',
                ].join(' ')
              }
            >
              <span
                className={`flex-shrink-0 transition-colors ${
                  isItemActive ? 'text-accent' : 'text-text-muted group-hover:text-text-primary'
                }`}
                aria-hidden="true"
              >
                <Icon size={18} />
              </span>

              {!collapsed && (
                <div className="flex flex-1 items-center justify-between min-w-0">
                  <span className="truncate">{item.label}</span>
                  {item.badge && (
                    <span className="rounded bg-accent/20 px-1.5 py-0.5 text-[9px] font-mono text-accent">
                      {item.badge}
                    </span>
                  )}
                </div>
              )}

              {/* Active left indicator strip */}
              {(isItemActive) && (
                <span
                  className="absolute left-0 top-1.5 bottom-1.5 w-1 rounded-r bg-accent"
                  aria-hidden="true"
                />
              )}
            </NavLink>
          )
        })}
      </nav>

      {/* Operational Footer */}
      <div className="border-t border-border bg-bg-subtle/60 p-3">
        {!collapsed ? (
          <div className="space-y-2">
            <div className="flex items-center justify-between text-[10px]">
              <span className="font-mono text-text-muted">SYSTEM CDS</span>
              <span className="inline-flex items-center gap-1 font-mono text-status-success font-medium">
                <span className="h-1.5 w-1.5 rounded-full bg-status-success animate-pulse" />
                ONLINE
              </span>
            </div>
            <div className="flex items-center justify-between text-[10px] text-text-muted">
              <span>Model: XGBoost v1.0</span>
              <span className="font-mono">v0.1.0</span>
            </div>
          </div>
        ) : (
          <div className="flex justify-center" title="System Online · XGBoost v1.0">
            <span className="h-2 w-2 rounded-full bg-status-success animate-pulse" />
          </div>
        )}
      </div>
    </aside>
  )
}
