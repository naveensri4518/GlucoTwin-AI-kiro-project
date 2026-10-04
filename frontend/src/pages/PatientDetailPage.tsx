import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { usePatientEhr, usePatientTwinState } from '@/hooks/usePatientDetail'
import {
  usePatientPredictions,
  usePrediction,
  useCreatePrediction,
} from '@/hooks/usePredictions'
import { useCgmHistory } from '@/hooks/useCgmHistory'
import type { EhrRecord, TwinState, DynamicLayer } from '@/types/digitalTwin'
import type { Prediction, RiskCategory, ContributingFactor } from '@/types/prediction'
import { ApiError } from '@/lib/apiClient'
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Dot,
} from 'recharts'

// ── Helpers ───────────────────────────────────────────────────────────────────

function fmtDate(iso: string | null | undefined): string {
  if (!iso) return '—'
  try {
    return new Intl.DateTimeFormat('en-GB', {
      day: '2-digit', month: 'short', year: 'numeric',
    }).format(new Date(iso))
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

// ── Shared primitives (unchanged from Phase 4) ────────────────────────────────

function ProvenanceBadge({ label }: { label: string }) {
  const isPredicted = label === 'PREDICTED'
  return (
    <span
      className={`ml-1.5 rounded border px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide ${
        isPredicted
          ? 'border-accent/40 text-accent'
          : 'border-slate-600 text-slate-400'
      }`}
    >
      {label}
    </span>
  )
}

function StatusBadge({ status }: { status: TwinState['status'] }) {
  const styles: Record<TwinState['status'], string> = {
    ACTIVE:      'bg-risk-low/10 text-risk-low border-risk-low/30',
    STALE:       'bg-risk-moderate/10 text-risk-moderate border-risk-moderate/30',
    INITIALISED: 'bg-slate-700/40 text-slate-400 border-slate-600',
    ARCHIVED:    'bg-slate-800 text-slate-500 border-slate-700',
  }
  return (
    <span className={`rounded border px-2 py-0.5 text-[11px] font-semibold ${styles[status]}`}>
      {status}
    </span>
  )
}

function Row({
  label,
  value,
  children,
}: {
  label: string
  value?: string
  children?: React.ReactNode
}) {
  return (
    <div className="flex items-baseline justify-between gap-2 border-b border-surface-border py-2 last:border-0">
      <span className="flex-shrink-0 text-[11px] text-slate-500">{label}</span>
      <span className="text-right text-xs font-medium text-slate-200">
        {children ?? value ?? '—'}
      </span>
    </div>
  )
}

function SectionCard({
  title,
  badge,
  children,
  className = '',
}: {
  title: string
  badge?: React.ReactNode
  children: React.ReactNode
  className?: string
}) {
  return (
    <div className={`card p-5 ${className}`}>
      <div className="mb-3 flex items-center gap-2">
        <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400">
          {title}
        </h3>
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

// ── EHR card (unchanged) ──────────────────────────────────────────────────────

function EhrCard({ ehr }: { ehr: EhrRecord }) {
  return (
    <SectionCard title="EHR Summary" badge={<ProvenanceBadge label="OBSERVED" />}>
      <Row label="Date of Birth"   value={fmtDate(ehr.dateOfBirth)} />
      <Row label="Sex"             value={ehr.sex} />
      <Row label="BMI"             value={fmtNum(ehr.bmi, 1, 'kg/m²')} />
      <Row label="Diabetes Onset"  value={fmtDate(ehr.diabetesOnsetDate)} />
      <Row label="HbA1c"           value={fmtNum(ehr.hba1c, 1, '%')} />
      <Row label="Fasting Glucose" value={fmtNum(ehr.fastingGlucose, 1, 'mmol/L')} />
    </SectionCard>
  )
}

// ── Twin meta card (unchanged) ────────────────────────────────────────────────

function TwinMetaCard({ twin }: { twin: TwinState }) {
  return (
    <SectionCard title="Digital Twin State">
      <Row label="Status">
        <StatusBadge status={twin.status} />
      </Row>
      <Row label="Version"      value={`v${twin.twinVersion}`} />
      <Row label="Created"      value={fmtDateTime(twin.createdAt)} />
      <Row label="Last Updated" value={fmtDateTime(twin.lastUpdatedAt)} />
    </SectionCard>
  )
}

// ── Glucose card (unchanged) ──────────────────────────────────────────────────

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
      <p className="mt-2 text-[10px] text-slate-600">Wearable sensor reading — not a prediction.</p>
      {dl.eventTimestamp && (
        <p className="mt-1 text-[10px] text-slate-500">
          Recorded:{' '}
          <time dateTime={dl.eventTimestamp}>{fmtDateTime(dl.eventTimestamp)}</time>
        </p>
      )}
    </SectionCard>
  )
}

// ── Vitals card (unchanged) ───────────────────────────────────────────────────

function VitalsCard({ dl }: { dl: DynamicLayer }) {
  return (
    <SectionCard title="Wearable Vitals" badge={<ProvenanceBadge label="OBSERVED" />}>
      <Row label="Heart Rate"     value={fmtNum(dl.heartRate, 0, 'bpm')} />
      <Row label="HRV"            value={fmtNum(dl.hrv, 0, 'ms')} />
      <Row label="Sleep Duration" value={fmtNum(dl.sleepDuration, 1, 'hrs')} />
      <Row label="Sleep Stage"    value={dl.sleepStage ?? '—'} />
      <Row label="Steps Today"    value={dl.stepCount != null ? dl.stepCount.toLocaleString() : '—'} />
      <Row label="Activity Level" value={dl.activityLevel ?? '—'} />
      <Row label="CGM Buffer"     value={dl.cgmHistorySize.toString()} />
    </SectionCard>
  )
}

// ── Data quality section (unchanged) ─────────────────────────────────────────

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
          <li key={w} className="text-xs text-slate-400">• {w}</li>
        ))}
      </ul>
    </div>
  )
}

// ── NEW: Risk category badge ──────────────────────────────────────────────────

function RiskBadge({ category }: { category: RiskCategory }) {
  const styles: Record<RiskCategory, string> = {
    LOW:      'badge-low',
    MODERATE: 'badge-moderate',
    HIGH:     'badge-high',
    CRITICAL: 'badge-critical',
  }
  return (
    <span className={`rounded border px-2.5 py-1 text-sm font-bold ${styles[category]}`}>
      {category}
    </span>
  )
}

// ── NEW: Prediction status badge ──────────────────────────────────────────────

function PredStatusBadge({ status }: { status: Prediction['status'] }) {
  const styles = {
    PENDING:   'bg-slate-700/40 text-slate-400 border-slate-600',
    COMPLETED: 'bg-risk-low/10 text-risk-low border-risk-low/30',
    FAILED:    'bg-risk-high/10 text-risk-high border-risk-high/30',
  }
  return (
    <span className={`rounded border px-1.5 py-0.5 text-[10px] font-semibold ${styles[status]}`}>
      {status}
    </span>
  )
}

// ── NEW: Latest prediction card ───────────────────────────────────────────────

function LatestPredictionCard({ prediction }: { prediction: Prediction }) {
  const prob = prediction.spikeProbability

  if (prediction.status === 'FAILED') {
    return (
      <SectionCard
        title="2-Hour Glucose Spike Prediction"
        badge={<ProvenanceBadge label="PREDICTED" />}
        className="col-span-full"
      >
        <div className="rounded border border-risk-high/30 bg-risk-high/5 p-3" role="alert">
          <p className="text-xs font-semibold text-risk-high">Prediction failed</p>
          {prediction.failureReason && (
            <p className="mt-1 text-[11px] text-slate-500">{prediction.failureReason}</p>
          )}
        </div>
        <SafetyDisclaimer />
      </SectionCard>
    )
  }

  if (prediction.status === 'PENDING' || prob == null) {
    return (
      <SectionCard
        title="2-Hour Glucose Spike Prediction"
        badge={<ProvenanceBadge label="PREDICTED" />}
        className="col-span-full"
      >
        <div className="flex items-center gap-2 py-2">
          <span className="inline-block h-3 w-3 animate-spin rounded-full border-2 border-accent border-t-transparent" aria-hidden="true" />
          <span className="text-xs text-slate-400">Computing prediction…</span>
        </div>
        <SafetyDisclaimer />
      </SectionCard>
    )
  }

  const ci = prediction.confidenceInterval

  return (
    <SectionCard
      title="2-Hour Glucose Spike Prediction"
      badge={<ProvenanceBadge label="PREDICTED" />}
      className="col-span-full"
    >
      {/* Main probability + risk */}
      <div className="mb-4 flex flex-wrap items-center gap-4">
        <div>
          <p className="text-[10px] text-slate-500">Spike probability</p>
          <p className="mt-0.5 text-4xl font-bold tabular-nums text-slate-100">
            {(prob * 100).toFixed(1)}
            <span className="text-xl text-slate-400">%</span>
          </p>
        </div>
        {prediction.riskCategory && (
          <div>
            <p className="text-[10px] text-slate-500">Risk category</p>
            <div className="mt-1">
              <RiskBadge category={prediction.riskCategory} />
            </div>
          </div>
        )}
        {ci && (
          <div>
            <p className="text-[10px] text-slate-500">95% confidence interval</p>
            <p className="mt-0.5 font-mono text-xs text-slate-300">
              [{(ci.low * 100).toFixed(1)}% – {(ci.high * 100).toFixed(1)}%]
            </p>
          </div>
        )}
      </div>

      {/* Meta row */}
      <div className="mb-4 flex flex-wrap gap-x-5 gap-y-1 border-t border-surface-border pt-3 text-[11px] text-slate-500">
        <span>Horizon: {prediction.predictionHorizonHours}h</span>
        <span>Model: {prediction.modelVersion}</span>
        <span>Twin v{prediction.twinStateVersion}</span>
        <span>
          At: <time dateTime={prediction.predictedAt}>{fmtDateTime(prediction.predictedAt)}</time>
        </span>
      </div>

      {/* Data quality warnings on this prediction */}
      {prediction.dataQualityWarnings.length > 0 && (
        <DataQualitySection warnings={prediction.dataQualityWarnings} />
      )}

      {/* Contributing factors */}
      {prediction.topContributingFactors.length > 0 && (
        <ContributingFactorsSection factors={prediction.topContributingFactors} />
      )}

      <SafetyDisclaimer />
    </SectionCard>
  )
}

// ── NEW: Contributing factors ─────────────────────────────────────────────────

function ContributingFactorsSection({ factors }: { factors: ContributingFactor[] }) {
  const maxContrib = Math.max(...factors.map((f) => f.contribution), 0.01)

  return (
    <div className="mt-4 border-t border-surface-border pt-4">
      <p className="mb-3 text-[11px] font-semibold uppercase tracking-wider text-slate-400">
        Top Contributing Factors
      </p>
      <p className="mb-3 text-[10px] text-slate-600">
        Model feature contributions (SHAP values). These are statistical drivers of the
        prediction — not medical diagnoses or treatment recommendations.
      </p>
      <ul className="flex flex-col gap-2" role="list">
        {factors.map((f) => (
          <li key={f.factorName} className="flex flex-col gap-1">
            <div className="flex items-center justify-between">
              <span className="font-mono text-[11px] text-slate-300">{f.factorName}</span>
              <span
                className={`text-[11px] font-medium ${
                  f.direction === 'INCREASES_RISK' ? 'text-risk-high' : 'text-risk-low'
                }`}
              >
                {f.direction === 'INCREASES_RISK' ? '↑' : '↓'}{' '}
                {(f.contribution * 100).toFixed(1)}%
              </span>
            </div>
            {/* Simple bar */}
            <div className="h-1 w-full overflow-hidden rounded-full bg-surface">
              <div
                className={`h-full rounded-full ${
                  f.direction === 'INCREASES_RISK' ? 'bg-risk-high' : 'bg-risk-low'
                }`}
                style={{ width: `${(f.contribution / maxContrib) * 100}%` }}
                role="presentation"
              />
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}

// ── NEW: Safety disclaimer ────────────────────────────────────────────────────

function SafetyDisclaimer() {
  return (
    <p className="mt-4 rounded border border-surface-border bg-surface px-3 py-2 text-[10px] text-slate-500">
      <strong className="text-slate-400">Decision support only.</strong> Predictions are
      probabilistic estimates — not medical diagnoses or treatment recommendations.
      Always apply clinical judgment.
    </p>
  )
}

// ── NEW: Generate prediction button ──────────────────────────────────────────

function GeneratePredictionButton({
  patientId,
  onCreated,
}: {
  patientId: string
  onCreated: (predictionId: string) => void
}) {
  const mutation = useCreatePrediction(patientId)

  async function handleClick() {
    try {
      const resp = await mutation.mutateAsync()
      onCreated(resp.predictionId)
    } catch {
      // error displayed below
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <button
        onClick={() => void handleClick()}
        disabled={mutation.isPending}
        className="flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-xs font-semibold text-white transition-colors hover:bg-accent-hover disabled:opacity-60 focus:outline-none focus:ring-2 focus:ring-accent"
        aria-label="Generate a new 2-hour glucose spike prediction"
      >
        {mutation.isPending && (
          <span
            className="inline-block h-3 w-3 animate-spin rounded-full border-2 border-white border-t-transparent"
            aria-hidden="true"
          />
        )}
        {mutation.isPending ? 'Generating…' : '⚡ Generate Prediction'}
      </button>
      {mutation.isError && (
        <p className="text-[11px] text-risk-high" role="alert">
          {mutation.error instanceof Error
            ? mutation.error.message
            : 'Failed to generate prediction'}
        </p>
      )}
    </div>
  )
}

// ── NEW: Prediction history ───────────────────────────────────────────────────

function PredictionHistory({ patientId }: { patientId: string }) {
  const { data, isLoading, isError, error } = usePatientPredictions(patientId)

  return (
    <div className="card p-5">
      <h3 className="mb-4 text-xs font-semibold uppercase tracking-wider text-slate-400">
        Prediction History
        <ProvenanceBadge label="PREDICTED" />
      </h3>

      {isLoading && (
        <div className="space-y-2">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-8 animate-pulse rounded bg-surface" aria-hidden="true" />
          ))}
        </div>
      )}

      {isError && (
        <p className="text-xs text-risk-high" role="alert">
          {error instanceof Error ? error.message : 'Failed to load prediction history'}
        </p>
      )}

      {!isLoading && !isError && (!data?.content || data.content.length === 0) && (
        <p className="text-xs text-slate-500">
          No predictions yet. Use "Generate Prediction" to create the first one.
        </p>
      )}

      {data && data.content.length > 0 && (
        <>
          <div className="overflow-x-auto">
            <table className="w-full text-[11px]" aria-label="Prediction history">
              <thead>
                <tr className="border-b border-surface-border text-slate-500">
                  <th className="pb-2 pr-4 text-left font-medium">Date / Time</th>
                  <th className="pb-2 pr-4 text-left font-medium">Probability</th>
                  <th className="pb-2 pr-4 text-left font-medium">Risk</th>
                  <th className="pb-2 text-left font-medium">Status</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((p) => (
                  <tr
                    key={p.predictionId}
                    className="border-b border-surface-border/60 last:border-0"
                  >
                    <td className="py-2 pr-4 text-slate-300">
                      <time dateTime={p.predictedAt}>{fmtDateTime(p.predictedAt)}</time>
                    </td>
                    <td className="py-2 pr-4 tabular-nums text-slate-200">
                      {p.spikeProbability != null
                        ? `${(p.spikeProbability * 100).toFixed(1)}%`
                        : '—'}
                    </td>
                    <td className="py-2 pr-4">
                      {p.riskCategory ? (
                        <RiskBadge category={p.riskCategory} />
                      ) : (
                        <span className="text-slate-500">—</span>
                      )}
                    </td>
                    <td className="py-2">
                      <PredStatusBadge status={p.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="mt-2 text-[10px] text-slate-600">
            Showing {data.content.length} of {data.page.totalElements} predictions
          </p>
        </>
      )}
    </div>
  )
}

// ── NEW: Prediction dashboard section ────────────────────────────────────────

function PredictionDashboard({ patientId }: { patientId: string }) {
  // Track the most recently created predictionId so we can poll its status
  const [latestPredictionId, setLatestPredictionId] = useState<string | null>(null)

  // Poll the latest prediction until COMPLETED/FAILED
  const latestPrediction = usePrediction(latestPredictionId ?? undefined)

  // Also watch prediction history for an existing latest
  const history = usePatientPredictions(patientId, 1)
  const historyLatest = history.data?.content?.[0]

  // Display preference: freshly created prediction > most recent from history
  const displayPrediction =
    latestPrediction.data ?? (historyLatest?.status === 'COMPLETED' ? historyLatest : undefined)

  return (
    <div className="mt-6">
      {/* Section header */}
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-sm font-semibold text-slate-200">Prediction Dashboard</h2>
        <GeneratePredictionButton
          patientId={patientId}
          onCreated={(id) => setLatestPredictionId(id)}
        />
      </div>

      <div className="grid gap-4 xl:grid-cols-3">
        {/* Latest prediction — spans all columns on xl */}
        {latestPrediction.isLoading && <LoadingCard />}
        {latestPrediction.isError && (
          <ErrorCard
            title="Prediction unavailable"
            message={
              latestPrediction.error instanceof Error
                ? latestPrediction.error.message
                : 'Failed to load prediction'
            }
          />
        )}
        {displayPrediction && (
          <LatestPredictionCard prediction={displayPrediction} />
        )}
        {!latestPredictionId && !displayPrediction && !latestPrediction.isLoading && (
          <div className="card col-span-full p-6 text-center">
            <p className="text-xs text-slate-500">
              No prediction generated yet. Click{' '}
              <strong className="text-slate-300">⚡ Generate Prediction</strong> above.
            </p>
            <SafetyDisclaimer />
          </div>
        )}

        {/* Prediction history */}
        <div className="xl:col-span-3">
          <PredictionHistory patientId={patientId} />
        </div>
      </div>
    </div>
  )
}

// ── NEW: CGM history chart ────────────────────────────────────────────────────

/** Format ISO timestamp to a compact "HH:mm" label for the chart x-axis. */
function fmtChartTime(iso: string): string {
  try {
    return new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit' }).format(
      new Date(iso)
    )
  } catch {
    return iso
  }
}

interface ChartPoint {
  time: string       // display label for x-axis
  isoTime: string    // original ISO for tooltip
  value: number
}

function CgmHistorySection({ patientId }: { patientId: string }) {
  const { data, isLoading, isError, error } = useCgmHistory(patientId)

  // ── Loading ──
  if (isLoading) {
    return (
      <div className="card mt-6 p-5">
        <div className="mb-3 flex items-center gap-2">
          <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-400">
            Glucose History
          </h2>
        </div>
        <div className="h-40 animate-pulse rounded bg-surface" aria-hidden="true" />
        <span className="sr-only">Loading glucose history…</span>
      </div>
    )
  }

  // ── Error (section-isolated — does not affect the rest of the page) ──
  if (isError) {
    return (
      <div className="card mt-6 p-5" role="alert">
        <h2 className="mb-2 text-xs font-semibold uppercase tracking-wider text-slate-400">
          Glucose History
        </h2>
        <p className="text-xs text-risk-high">
          {error instanceof Error ? error.message : 'Failed to load glucose history'}
        </p>
      </div>
    )
  }

  // Sort chronologically (oldest → newest) for the chart
  const readings = [...(data ?? [])].sort(
    (a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime()
  )

  const chartPoints: ChartPoint[] = readings.map((r) => ({
    time: fmtChartTime(r.timestamp),
    isoTime: r.timestamp,
    value: r.value,
  }))

  // Summary metrics — derived only from API response, never fabricated
  const values = readings.map((r) => r.value)
  const latest = readings.length > 0 ? readings[readings.length - 1] : null
  const minVal  = values.length > 0 ? Math.min(...values) : null
  const maxVal  = values.length > 0 ? Math.max(...values) : null

  return (
    <div className="card mt-6 p-5">
      {/* Header */}
      <div className="mb-4 flex items-center gap-2">
        <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-400">
          Glucose History
        </h2>
        <span className="rounded border border-slate-600 px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide text-slate-400">
          OBSERVED
        </span>
      </div>
      <p className="mb-4 text-[11px] text-slate-500">
        Observed wearable glucose readings from the Digital Twin rolling buffer.
        Not a prediction — these are raw sensor values.
      </p>

      {/* ── Empty state ── */}
      {readings.length === 0 && (
        <div className="flex h-32 flex-col items-center justify-center rounded border border-dashed border-surface-border text-center">
          <p className="text-xs text-slate-400">No glucose history available yet.</p>
          <p className="mt-1 text-[11px] text-slate-600">
            Wearable glucose events must be received before history can be displayed.
          </p>
        </div>
      )}

      {/* ── Summary metrics + chart ── */}
      {readings.length > 0 && (
        <>
          {/* Summary row */}
          <div className="mb-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
            <MetricTile
              label="Latest observed"
              value={latest ? `${latest.value.toFixed(1)} mmol/L` : '—'}
              highlight
            />
            <MetricTile
              label="Minimum"
              value={minVal != null ? `${minVal.toFixed(1)} mmol/L` : '—'}
            />
            <MetricTile
              label="Maximum"
              value={maxVal != null ? `${maxVal.toFixed(1)} mmol/L` : '—'}
            />
            <MetricTile
              label="Readings"
              value={readings.length.toString()}
            />
          </div>

          {/* Latest reading highlight */}
          {latest && (
            <div className="mb-5 flex items-center gap-3 rounded border border-surface-border bg-surface px-4 py-3">
              <div>
                <p className="text-[10px] text-slate-500">
                  Latest observed glucose{' '}
                  <span className="rounded border border-slate-600 px-1 py-0.5 text-[10px] font-medium uppercase text-slate-400">
                    OBSERVED
                  </span>
                </p>
                <p className="mt-0.5 text-2xl font-bold tabular-nums text-slate-100">
                  {latest.value.toFixed(1)}{' '}
                  <span className="text-sm font-normal text-slate-500">mmol/L</span>
                </p>
                <p className="mt-0.5 text-[11px] text-slate-500">
                  <time dateTime={latest.timestamp}>{fmtDateTime(latest.timestamp)}</time>
                </p>
              </div>
            </div>
          )}

          {/* Recharts line chart */}
          <div aria-label="Glucose history chart" role="img">
            <ResponsiveContainer width="100%" height={180}>
              <LineChart
                data={chartPoints}
                margin={{ top: 4, right: 8, left: -16, bottom: 0 }}
              >
                <CartesianGrid
                  strokeDasharray="3 3"
                  stroke="#2e3340"
                  vertical={false}
                />
                <XAxis
                  dataKey="time"
                  tick={{ fill: '#64748b', fontSize: 10 }}
                  axisLine={{ stroke: '#2e3340' }}
                  tickLine={false}
                />
                <YAxis
                  domain={['auto', 'auto']}
                  tick={{ fill: '#64748b', fontSize: 10 }}
                  axisLine={false}
                  tickLine={false}
                  tickFormatter={(v: number) => v.toFixed(1)}
                  unit=" mmol"
                />
                <Tooltip
                  contentStyle={{
                    backgroundColor: '#21252e',
                    border: '1px solid #2e3340',
                    borderRadius: '6px',
                    fontSize: '11px',
                    color: '#e2e8f0',
                  }}
                  formatter={(value: number) => [
                    `${value.toFixed(1)} mmol/L`,
                    'Glucose (OBSERVED)',
                  ]}
                  labelFormatter={(_: string, payload: { payload?: ChartPoint }[]) => {
                    const iso = payload?.[0]?.payload?.isoTime
                    return iso ? fmtDateTime(iso) : ''
                  }}
                />
                <Line
                  type="monotone"
                  dataKey="value"
                  stroke="#3b82f6"
                  strokeWidth={2}
                  dot={<Dot r={3} fill="#3b82f6" stroke="#21252e" strokeWidth={1} />}
                  activeDot={{ r: 5, fill: '#3b82f6' }}
                  isAnimationActive={false}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>

          <p className="mt-2 text-[10px] text-slate-600">
            Rolling buffer of observed glucose readings (wearable sensor data only).
          </p>
        </>
      )}
    </div>
  )
}

function MetricTile({
  label,
  value,
  highlight = false,
}: {
  label: string
  value: string
  highlight?: boolean
}) {
  return (
    <div className="rounded border border-surface-border bg-surface px-3 py-2">
      <p className="text-[10px] text-slate-500">{label}</p>
      <p
        className={`mt-0.5 text-sm font-semibold tabular-nums ${
          highlight ? 'text-accent' : 'text-slate-200'
        }`}
      >
        {value}
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
          <span className={isFetching ? 'animate-spin inline-block' : ''} aria-hidden="true">
            ↻
          </span>{' '}
          Refresh
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

      {/* ── Top-level data quality warnings ── */}
      {twin.data?.dynamicLayer?.dataQualityWarnings?.length ? (
        <div className="mb-4">
          <DataQualitySection warnings={twin.data.dynamicLayer.dataQualityWarnings} />
        </div>
      ) : null}

      {!twinNotFound && (
        <>
          {/* ── Digital Twin grid (Phase 4, unchanged) ── */}
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {/* EHR */}
            {ehr.isLoading && <LoadingCard />}
            {ehr.isError && !(ehr.error instanceof ApiError && ehr.error.status === 404) && (
              <ErrorCard
                title="EHR unavailable"
                message={ehr.error instanceof Error ? ehr.error.message : 'Failed to load EHR'}
              />
            )}
            {ehr.isError && ehr.error instanceof ApiError && ehr.error.status === 404 && (
              <SectionCard title="EHR Summary">
                <p className="text-xs text-slate-500">No EHR record uploaded yet.</p>
              </SectionCard>
            )}
            {ehr.data && <EhrCard ehr={ehr.data} />}

            {/* Twin state */}
            {twin.isLoading && <LoadingCard />}
            {twin.isError && !twinNotFound && (
              <ErrorCard
                title="Twin state unavailable"
                message={twin.error instanceof Error ? twin.error.message : 'Failed to load twin state'}
              />
            )}
            {twin.data && <TwinMetaCard twin={twin.data} />}

            {/* Current glucose */}
            {twin.isLoading && <LoadingCard />}
            {twin.data?.dynamicLayer && <GlucoseCard dl={twin.data.dynamicLayer} />}
            {twin.data && !twin.data.dynamicLayer && (
              <SectionCard title="Current Glucose">
                <p className="text-xs text-slate-500">
                  No wearable events yet — twin is{' '}
                  <span className="font-medium text-slate-300">INITIALISED</span>.
                </p>
              </SectionCard>
            )}

            {/* Wearable vitals */}
            {twin.data?.dynamicLayer && <VitalsCard dl={twin.data.dynamicLayer} />}
          </div>

          {/* ── CGM History chart (Phase 6B) ── */}
          {patientId && <CgmHistorySection patientId={patientId} />}

          {/* ── Prediction Dashboard (Phase 5, new) ── */}
          {patientId && <PredictionDashboard patientId={patientId} />}
        </>
      )}
    </div>
  )
}
