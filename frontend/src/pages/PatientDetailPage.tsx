import { Link, useParams } from 'react-router-dom'

/**
 * Patient detail page — placeholder until Phase 3 wires up the APIs.
 * Will consume:
 *   GET /api/v1/patients/:patientId/ehr
 *   GET /api/v1/patients/:patientId/twin-state
 *   GET /api/v1/patients/:patientId/predictions
 */
export default function PatientDetailPage() {
  const { patientId } = useParams<{ patientId: string }>()

  return (
    <div>
      <div className="mb-6 flex items-center gap-3">
        <Link
          to="/patients"
          className="text-xs text-slate-400 hover:text-slate-100"
          aria-label="Back to patient list"
        >
          ← Patients
        </Link>
        <span className="text-slate-600" aria-hidden="true">/</span>
        <span className="text-xs text-slate-300 font-mono">{patientId}</span>
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        {/* EHR Summary placeholder */}
        <div className="card p-5">
          <h3 className="mb-3 text-xs font-semibold uppercase tracking-wider text-slate-400">
            EHR Summary
          </h3>
          <p className="text-xs text-slate-500">
            Phase 3 — will connect to{' '}
            <code className="rounded bg-surface px-1 font-mono text-accent">GET /ehr</code>
          </p>
        </div>

        {/* Digital Twin State placeholder */}
        <div className="card p-5">
          <h3 className="mb-3 text-xs font-semibold uppercase tracking-wider text-slate-400">
            Digital Twin State
          </h3>
          <p className="text-xs text-slate-500">
            Phase 3 — will connect to{' '}
            <code className="rounded bg-surface px-1 font-mono text-accent">
              GET /twin-state
            </code>
          </p>
        </div>

        {/* Spike Prediction placeholder */}
        <div className="card p-5">
          <h3 className="mb-3 text-xs font-semibold uppercase tracking-wider text-slate-400">
            Glucose Spike Prediction
          </h3>
          <p className="text-xs text-slate-500">
            Phase 3 — will connect to{' '}
            <code className="rounded bg-surface px-1 font-mono text-accent">
              POST /predictions
            </code>
          </p>
          <p className="mt-2 text-[10px] text-slate-600">
            spikeProbability is a probabilistic estimate, not a certainty.
          </p>
        </div>
      </div>

      {/* CGM chart placeholder */}
      <div className="card mt-4 p-5">
        <h3 className="mb-3 text-xs font-semibold uppercase tracking-wider text-slate-400">
          CGM History (OBSERVED)
        </h3>
        <div className="flex h-32 items-center justify-center rounded border border-dashed border-surface-border">
          <p className="text-xs text-slate-600">
            Recharts glucose chart — Phase 4
          </p>
        </div>
      </div>
    </div>
  )
}
