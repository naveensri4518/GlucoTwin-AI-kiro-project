import { NavLink } from 'react-router-dom'

type NavItem = { label: string; to: string; icon: string }

const NAV_ITEMS: NavItem[] = [
  { label: 'Patients', to: '/patients', icon: '👤' },
]

export default function Sidebar() {
  return (
    <nav
      className="flex w-56 flex-shrink-0 flex-col border-r border-surface-border bg-surface-raised"
      aria-label="Primary navigation"
    >
      {/* Logo */}
      <div className="flex items-center gap-2 border-b border-surface-border px-5 py-4">
        <span className="text-lg font-semibold tracking-tight text-accent" aria-hidden="true">
          ⟳
        </span>
        <div>
          <p className="text-sm font-semibold leading-none text-slate-100">GlucoTwin</p>
          <p className="text-[10px] text-slate-400">Clinical Decision Support</p>
        </div>
      </div>

      {/* Navigation links */}
      <ul className="flex flex-1 flex-col gap-1 p-3" role="list">
        {NAV_ITEMS.map((item) => (
          <li key={item.to}>
            <NavLink
              to={item.to}
              className={({ isActive }) =>
                [
                  'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-accent/10 text-accent'
                    : 'text-slate-400 hover:bg-surface hover:text-slate-100',
                ].join(' ')
              }
            >
              <span aria-hidden="true">{item.icon}</span>
              {item.label}
            </NavLink>
          </li>
        ))}
      </ul>

      {/* Version footer */}
      <div className="border-t border-surface-border px-5 py-3">
        <p className="text-[10px] text-slate-500">v0.1.0 · Prototype</p>
      </div>
    </nav>
  )
}
