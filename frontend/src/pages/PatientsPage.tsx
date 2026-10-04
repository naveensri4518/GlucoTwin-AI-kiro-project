import { Link } from 'react-router-dom'

/**
 * Patient list page — placeholder until Phase 3 wires up the API.
 * Will consume GET /api/v1/patients via TanStack Query.
 */
export default function PatientsPage() {
  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold text-slate-100">Patients</h2>
          <p className="mt-0.5 text-xs text-slate-400">
            Select a patient to view their Digital Twin and glucose predictions.
          </p>
        </div>
      </div>

      {/* Placeholder — Phase 3 will replace with live data */}
      <div className="card p-8 text-center">
        <p className="text-2xl" aria-hidden="true">👤</p>
        <p className="mt-3 text-sm font-medium text-slate-300">Patient list coming in Phase 3</p>
        <p className="mt-1 text-xs text-slate-500">
          Will connect to{' '}
          <code className="rounded bg-surface px-1 py-0.5 font-mono text-[11px] text-accent">
            GET /api/v1/patients
          </code>
        </p>

        {/* Demo navigation to show routing works */}
        <div className="mt-6 flex justify-center gap-3">
          <Link
            to="/patients/a1000000-0000-0000-0000-000000000001"
            className="rounded-md bg-accent px-4 py-2 text-xs font-medium text-white transition-colors hover:bg-accent-hover"
          >
            Preview Patient P001 →
          </Link>
        </div>
      </div>
    </div>
  )
}
