import { Link, useParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { usePatientEhr, usePatientTwinState } from '@/hooks/usePatientDetail'
import type { EhrRecord, TwinState, DynamicLayer } from '@/types/digitalTwin'
import { ApiError } from '@/lib/apiClient'

// ── Helpers ──────────────────────────────────────────────────────────────────

function fmtDate(iso: string | null | undefined): string {
  if (!iso) return '—'
  try {
    return new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }).format(new Date(iso))
  } catch { return iso }
}

function fmtDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  try {
    return new Intl.DateTimeFormat('en-GB', {
      day: '2-digit', month: 'short', year: 'numeric',
      hour: '2-digit', minute: '2-digit', timeZoneName: 'short',
    }).format(new Date(iso))
  } catch { return iso }
}

function fmtNum(val: number | null | undefined, decimals = 1, unit = ''): string {
  if (val == null) return '—'
  return `${val.toFixed(decimals)}${unit ? ' ' + unit : ''}`
}

// ── Small reusable pieces ─────────────────────────────────────────────────────

function ProvenanceBadge({ label }: { label: string }) {
  return (
    <span className="ml-1.5 rounded border border-slate-600 px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide text-slate-400">
      {label}
    </span>
  )
}

function StatusBadge({ status }: { status: TwinState['status'] }) {
  const styles: Record<TwinState['status'], string> = {
    ACTIVE:       'bg-risk-low/10 text-risk-low border-risk-low/30',
    STALE:        'bg-risk-moderate/10 text-risk-moderate border-risk-moderate/30',
    INITIALISED:  'bg-slate-700/40 text-slate-400 border-slate-600',
    ARCHIVED:     'bg-slate-800 text-slate-500 border-slate-700',
  }
  return (
    <span className={`rounded border px-2 py-0.5 text-[11px] font-semibold ${styles[status]}`}>
      {status}
    </span>
  )
}

function Row({ label, value, children }: { label: string; value?: string; children?: React.ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-2 py-2 border-b border-surface-border last:border-0">
      <span className="flex-shrink-0 text-[11px] text-slate-500">{label}</span>
      <span className="text-right text-xs font-medium text-slate-200">
        {children ?? value ?? '—'}
      </span>
    </div>
  )
}

function SectionCard({ title, badge, children }: { title: string; badge?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="card p-5">
      <div className="mb-3 flex items-center gap-2">
        <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400">{title}</h3>
        {badge}
      </div>
      {children}
    </div>
  )
}

function LoadingCard() {
  return <div className="card h-40 animate-pulse bg-surface-raised" aria-hidden="true" />
}

function ErrorCard({ title, message }: { title: string; message: string }) {
  return (
    <div className="card p-5" role="alert">
      <p className="mb-1 text-xs font-semibold text-risk-high">{title}</p>
      <p className="text-[11px] text-slate-500">{message}</p>
    </div>
  )
}

// ── EHR card ─────────────────────────────────────────────────────────────────

function EhrCard({ ehr }: { ehr: EhrRecord }) {
  return (
    <SectionCard title="EHR Summary" badge={<ProvenanceBadge label="OBSERVED" />}>
      <Row label="Date of Birth"       value={fmtDate(ehr.dateOfBirth)} />
      <Row label="Sex"                 value={ehr.sex} />
      <Row label="BMI"                 value={fmtNum(ehr.bmi, 1, 'kg/m²')} />
      <Row label="Diabetes Onset"      value={fmtDate(ehr.diabetesOnsetDate)} />
      <Row label="HbA1c"               value={fmtNum(ehr.hba1c, 1, '%')} />
      <Row label="Fasting Glucose"     value={fmtNum(ehr.fastingGlucose, 1, 'mmol/L')} />
    </SectionCard>
  )
}

// ── Twin state meta card ──────────────────────────────────────────────────────

function TwinMetaCard({ twin }: { twin: TwinState }) {
  return (
    <SectionCard title="Digital Twin State">
      <Row label="Status">
        <StatusBadge status={twin.status} />
      </Row>
      <Row label="Version"       value={`v${twin.twinVersion}`} />
      <Row label="Created"       value={fmtDateTime(twin.createdAt)} />
      <Row label="Last Updated"  value={fmtDateTime(twin.lastUpdatedAt)} />
    </SectionCard>
  )
}

// ── Current glucose card ──────────────────────────────────────────────────────

function GlucoseCard({ dl }: { dl: DynamicLayer }) {
  const glucose = dl.glucoseReading
  const isHigh  = glucose != null && glucose >= 10.0
  const isMid   = glucose != null && glucose >= 7.8 && glucose < 10.0

  return (
    <SectionCard title="Current Glucose" badge={<ProvenanceBadge label="OBSERVED" />}>
      {glucose == null ? (
        <p className="text-xs text-slate-500">No glucose reading available</p>
      ) : (
        <div className="flex items-end gap-2">
          <span
            className={`text-3xl font-bold tabular-nums ${
              isHigh ? 'text-risk-high' : isMid ? 'text-risk-moderate' : 'text-risk-low'
            }`}
          >
            {glucose.toFixed(1)}
          </span>
          <span className="mb-1 text-xs text-slate-500">mmol/L</span>
        </div>
      )}
      <p className="mt-2 text-[10px] text-slate-600">
        Wearable sensor reading — not a prediction.
      </p>
      {dl.eventTimestamp && (
        <p className="mt-1 text-[10px] text-slate-500">
          Recorded: <time dateTime={dl.eventTimestamp}>{fmtDateTime(dl.eventTimestamp)}</time>
        </p>
      )}
    </SectionCard>
  )
}

// ── Wearable vitals card ──────────────────────────────────────────────────────

function VitalsCard({ dl }: { dl: DynamicLayer }) {
  return (
    <SectionCard title="Wearable Vitals" badge={<ProvenanceBadge label="OBSERVED" />}>
      <Row label="Heart Rate"      value={fmtNum(dl.heartRate, 0, 'bpm')} />
      <Row label="HRV"             value={fmtNum(dl.hrv, 0, 'ms')} />
      <Row label="Sleep Duration"  value={fmtNum(dl.sleepDuration, 1, 'hrs')} />
      <Row label="Sleep Stage"     value={dl.sleepStage ?? '—'} />
      <Row label="Steps Today"     value={dl.stepCount != null ? dl.stepCount.toLocaleString() : '—'} />
      <Row label="Activity Level"  value={dl.activityLevel ?? '—'} />
      <Row label="CGM Buffer Size" value={dl.cgmHistorySize.toString()} />
    </SectionCard>
  )
}

// ── Data quality warnings ─────────────────────────────────────────────────────

function DataQualitySection({ warnings }: { warnings: string[] }) {
  if (warnings.length === 0) return null
  return (
    <div
      className="card border-risk-moderate/40 bg-risk-moderate/5 p-4"
      role="alert"
      aria-live="polite"
    >
      <p className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-risk-moderate">
        ⚠ Data Quality Warnings
      </p>
      <ul className="flex flex-col gap-1" role="list">
        {warnings.map((w) => (
          <li key={w} className="text-xs text-slate-400">
            • {w}
          </li>
        ))}
      </ul>
    </div>
  )
}

// ── Prediction placeholder ─────────────────────────────────────────────────────

function PredictionPlaceholder() {
  return (
    <div className="card p-5 opacity-60">
      <h3 className="mb-3 text-xs font-semibold uppercase tracking-wider text-slate-400">
        Glucose Spike Prediction
      </h3>
      <p className="text-xs text-slate-500">Phase 5 — prediction dashboard coming next.</p>
      <p className="mt-1 text-[10px] text-slate-600">
        Will display 2-hour spike probability, confidence interval, and top risk factors.
      </p>
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function PatientDetailPage() {
  const { patientId } = useParams<{ patientId: string }>()
  const queryClient = useQueryClient()

  const ehr  = usePatientEhr(patientId)
  const twin = usePatientTwinState(patientId)

  const isFetching = ehr.isFetching || twin.isFetching

  function handleRefresh() {
    void queryClient.invalidateQueries({ queryKey: ['patients', patientId] })
  }

  // 404 on twin state = patient genuinely does not exist yet
  const twinNotFound =
    twin.isError && twin.error instanceof ApiError && twin.error.status === 404

  return (
    <div>
      {/* ── Header ── */}
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div className="flex min-w-0 items-center gap-3">
          <Link
            to="/patients"
            className="flex-shrink-0 text-xs text-slate-400 hover:text-slate-100"
            aria-label="Back to patient list"
          >
            ← Patients
          </Link>
          <span className="text-slate-600" aria-hidden="true">/</span>
          <span className="truncate font-mono text-xs text-slate-300">{patientId}</span>

          {twin.data && (
            <span className="hidden sm:block">
              <StatusBadge status={twin.data.status} />
            </span>
          )}
          {twin.data && (
            <span className="hidden text-[11px] text-slate-500 sm:block">
              v{twin.data.twinVersion} · updated {fmtDateTime(twin.data.lastUpdatedAt)}
            </span>
          )}
        </div>

        <button
          onClick={handleRefresh}
          disabled={isFetching}
          className="flex-shrink-0 rounded-md border border-surface-border bg-surface-raised px-3 py-1.5 text-xs text-slate-400 transition-colors hover:text-slate-100 disabled:opacity-50 focus:outline-none focus:ring-2 focus:ring-accent"
          aria-label="Refresh patient data"
        >
          <span className={isFetching ? 'animate-spin inline-block' : ''} aria-hidden="true">↻</span>
          {' '}Refresh
        </button>
      </div>

      {/* ── Patient not found ── */}
      {twinNotFound && (
        <div className="card p-10 text-center" role="alert">
          <p className="text-2xl" aria-hidden="true">🔍</p>
          <p className="mt-3 text-sm font-medium text-slate-300">Patient not found</p>
          <p className="mt-1 text-xs text-slate-500">
            No Digital Twin exists for ID:{' '}
            <span className="font-mono text-accent">{patientId}</span>
          </p>
          <Link
            to="/patients"
            className="mt-4 inline-block rounded-md bg-accent px-4 py-1.5 text-xs font-medium text-white hover:bg-accent-hover"
          >
            Back to patients
          </Link>
        </div>
      )}

      {/* ── Data Quality Warnings (top-level) ── */}
      {twin.data?.dynamicLayer?.dataQualityWarnings?.length ? (
        <div className="mb-4">
          <DataQualitySection warnings={twin.data.dynamicLayer.dataQualityWarnings} />
        </div>
      ) : null}

      {/* ── Main grid ── */}
      {!twinNotFound && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">

          {/* EHR */}
          {ehr.isLoading && <LoadingCard />}
          {ehr.isError && !( ehr.error instanceof ApiError && ehr.error.status === 404) && (
            <ErrorCard
              title="EHR unavailable"
              message={ehr.error instanceof Error ? ehr.error.message : 'Failed to load EHR data'}
            />
          )}
          {ehr.isError && ehr.error instanceof ApiError && ehr.error.status === 404 && (
            <SectionCard title="EHR Summary">
              <p className="text-xs text-slate-500">No EHR record uploaded for this patient yet.</p>
            </SectionCard>
          )}
          {ehr.data && <EhrCard ehr={ehr.data} />}

          {/* Twin metadata */}
          {twin.isLoading && <LoadingCard />}
          {twin.isError && !twinNotFound && (
            <ErrorCard
              title="Twin state unavailable"
              message={twin.error instanceof Error ? twin.error.message : 'Failed to load Digital Twin state'}
            />
          )}
          {twin.data && <TwinMetaCard twin={twin.data} />}

          {/* Current glucose */}
          {twin.isLoading && <LoadingCard />}
          {twin.data?.dynamicLayer && <GlucoseCard dl={twin.data.dynamicLayer} />}
          {twin.data && !twin.data.dynamicLayer && (
            <SectionCard title="Current Glucose">
              <p className="text-xs text-slate-500">
                No wearable events received yet — twin is{' '}
                <span className="font-medium text-slate-300">INITIALISED</span>.
              </p>
            </SectionCard>
          )}

          {/* Wearable vitals */}
          {twin.data?.dynamicLayer && <VitalsCard dl={twin.data.dynamicLayer} />}

          {/* Prediction placeholder */}
          <PredictionPlaceholder />
        </div>
      )}
    </div>
  )
}
