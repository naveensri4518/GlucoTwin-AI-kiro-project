import { useLocation } from 'react-router-dom'

const PAGE_TITLES: Record<string, string> = {
  '/patients': 'Patient List',
}

export default function TopBar() {
  const { pathname } = useLocation()

  const title =
    Object.entries(PAGE_TITLES).find(([key]) => pathname === key)?.[1] ??
    (pathname.startsWith('/patients/') ? 'Patient Detail' : 'GlucoTwin AI')

  return (
    <header className="flex h-14 flex-shrink-0 items-center justify-between border-b border-surface-border bg-surface-raised px-6">
      <h1 className="text-sm font-semibold text-slate-100">{title}</h1>
      <div className="flex items-center gap-3">
        <span className="rounded-full bg-accent/10 px-2 py-0.5 text-[10px] font-medium text-accent">
          CLINICIAN
        </span>
      </div>
    </header>
  )
}
