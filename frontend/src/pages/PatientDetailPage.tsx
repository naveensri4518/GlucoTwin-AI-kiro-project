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
import type { Prediction, ContributingFactor } from '@/types/prediction'
import { ApiError } from '@/lib/apiClient'
import { WhatIfSimulationPanel } from '@/components/WhatIfSimulationPanel'
import { ClinicalInsightPanel } from '@/components/ClinicalInsightPanel'
import { StatusBadge } from '@/components/common/StatusBadge'
import { ProvenanceBadge } from '@/components/common/ProvenanceBadge'
import { SectionCard } from '@/components/common/SectionCard'
import { LoadingSkeleton } from '@/components/common/LoadingSkeleton'
import {
  IconArrowLeft,
  IconRefresh,
  IconTwin,
} from '@/components/common/Icons'
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
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    }).format(new Date(iso))
  } catch {
    return iso
  }
}

function fmtDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  try {
    return new Intl.DateTimeFormat('en-GB', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(iso))
  } catch {
    return iso
  }
}

function fmtNum(val: number | null | undefined, decimals = 1, unit = ''): string {
  if (val == null) return '—'
  return `${val.toFixed(decimals)}${unit ? ' ' + unit : ''}`
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
    <div className="flex items-baseline justify-between gap-2 border-b border-border/60 py-2.5 last:border-0">
      <span className="flex-shrink-0 text-xs text-text-muted">{label}</span>
      <span className="text-right text-xs font-medium text-text-primary">
        {children ?? value ?? '—'}
      </span>
    </div>
  )
}

function MetricTile({
  label,
  value,
  highlight = false,
  unit,
}: {
  label: string
  value: string
  highlight?: boolean
  unit?: string
}) {
  return (
    <div className="rounded-lg border border-border bg-bg-card p-3">
      <p className="text-[10px] font-medium uppercase tracking-wider text-text-muted">{label}</p>
      <div className="mt-1 flex items-baseline gap-1">
        <span
          className={`text-lg font-bold tabular-nums ${
            highlight ? 'text-accent' : 'text-text-primary'
          }`}
        >
          {value}
        </span>
        {unit && <span className="text-xs text-text-muted">{unit}</span>}
      </div>
    </div>
  )
}

// ── ROW 1: EHR & Twin & Current Glucose Cards ────────────────────────────────

function EhrCard({ ehr }: { ehr: EhrRecord }) {
  return (
    <SectionCard title="EHR Summary" badge={<ProvenanceBadge label="OBSERVED" />}>
      <Row label="Date of Birth" value={fmtDate(ehr.dateOfBirth)} />
      <Row label="Biological Sex" value={ehr.sex} />
      <Row label="BMI" value={fmtNum(ehr.bmi, 1, 'kg/m²')} />
      <Row label="Diabetes Onset" value={fmtDate(ehr.diabetesOnsetDate)} />
      <Row label="HbA1c" value={fmtNum(ehr.hba1c, 1, '%')} />
      <Row label="Fasting Glucose" value={fmtNum(ehr.fastingGlucose, 1, 'mmol/L')} />
    </SectionCard>
  )
}

function TwinMetaCard({ twin }: { twin: TwinState }) {
  return (
    <SectionCard title="Digital Twin State" badge={<ProvenanceBadge label="OBSERVED" />}>
      <Row label="Status">
        <StatusBadge status={twin.status} />
      </Row>
      <Row label="Twin Architecture Version" value={`v${twin.twinVersion}`} />
      <Row label="Created Timestamp" value={fmtDateTime(twin.createdAt)} />
      <Row label="Last State Synchronization" value={fmtDateTime(twin.lastUpdatedAt)} />
    </SectionCard>
  )
}

function GlucoseCard({ dl }: { dl: DynamicLayer }) {
  const glucose = dl.glucoseReading
  const isHigh = glucose != null && glucose >= 10.0
  const isMid = glucose != null && glucose >= 7.8 && glucose < 10.0

  return (
    <SectionCard title="Current Glucose Telemetry" badge={<ProvenanceBadge label="OBSERVED" />}>
      {glucose == null ? (
        <p className="text-xs text-text-muted py-4">No sensor glucose reading currently available</p>
      ) : (
        <div>
          <div className="flex items-baseline gap-2">
            <span
              className={`text-4xl font-extrabold tabular-nums tracking-tight ${
                isHigh ? 'text-status-danger' : isMid ? 'text-status-warning' : 'text-status-success'
              }`}
            >
              {glucose.toFixed(1)}
            </span>
            <span className="text-sm font-medium text-text-muted">mmol/L</span>
            <span className="text-xs text-text-muted">
              ({(glucose * 18.018).toFixed(0)} mg/dL)
            </span>
          </div>
          <div className="mt-3 flex items-center gap-2">
            <span
              className={`inline-flex rounded-full px-2 py-0.5 text-[11px] font-semibold ${
                isHigh
                  ? 'bg-status-danger/10 text-status-danger border border-status-danger/30'
                  : isMid
                  ? 'bg-status-warning/10 text-status-warning border border-status-warning/30'
                  : 'bg-status-success/10 text-status-success border border-status-success/30'
              }`}
            >
              {isHigh ? 'Elevated Postprandial' : isMid ? 'Borderline Target' : 'In Target Range'}
            </span>
          </div>
          {dl.eventTimestamp && (
            <p className="mt-3 text-[11px] text-text-muted">
              Sampled: <time dateTime={dl.eventTimestamp}>{fmtDateTime(dl.eventTimestamp)}</time>
            </p>
          )}
        </div>
      )}
      <p className="mt-4 border-t border-border/60 pt-2 text-[10px] text-text-muted">
        Verified continuous biometric telemetry — strictly non-probabilistic.
      </p>
    </SectionCard>
  )
}

// ── ROW 2: Wearable Vitals Grid ──────────────────────────────────────────────

function WearableMonitoringSection({ dl }: { dl: DynamicLayer }) {
  return (
    <div id="wearable-monitoring" className="space-y-3">
      <div className="flex items-center gap-2">
        <h3 className="text-xs font-semibold uppercase tracking-wider text-text-secondary">
          Continuous Wearable Biometric Telemetry
        </h3>
        <ProvenanceBadge label="OBSERVED" />
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        <MetricTile label="Heart Rate" value={fmtNum(dl.heartRate, 0)} unit="bpm" />
        <MetricTile label="HRV" value={fmtNum(dl.hrv, 0)} unit="ms" />
        <MetricTile label="Sleep Duration" value={fmtNum(dl.sleepDuration, 1)} unit="hrs" />
        <MetricTile label="Sleep Stage" value={dl.sleepStage ?? '—'} />
        <MetricTile
          label="Steps Today"
          value={dl.stepCount != null ? dl.stepCount.toLocaleString() : '—'}
        />
        <MetricTile
          label="CGM Buffer"
          value={dl.cgmHistorySize.toString()}
          unit="readings"
          highlight
        />
      </div>
    </div>
  )
}

// ── ROW 3: Full-Width Glucose History Chart ──────────────────────────────────

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
  time: string
  isoTime: string
  value: number
}

function CgmHistorySection({ patientId }: { patientId: string }) {
  const { data, isLoading, isError, error } = useCgmHistory(patientId)

  if (isLoading) {
    return (
      <SectionCard title="Glucose History Telemetry" badge={<ProvenanceBadge label="OBSERVED" />}>
        <LoadingSkeleton rows={2} height="h-32" />
      </SectionCard>
    )
  }

  if (isError) {
    return (
      <SectionCard title="Glucose History Telemetry" badge={<ProvenanceBadge label="OBSERVED" />}>
        <p className="text-xs text-status-danger">
          {error instanceof Error ? error.message : 'Failed to load glucose telemetry history'}
        </p>
      </SectionCard>
    )
  }

  const readings = [...(data ?? [])].sort(
    (a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime()
  )

  const chartPoints: ChartPoint[] = readings.map((r) => ({
    time: fmtChartTime(r.timestamp),
    isoTime: r.timestamp,
    value: r.value,
  }))

  const values = readings.map((r) => r.value)
  const latest = readings.length > 0 ? readings[readings.length - 1] : null
  const minVal = values.length > 0 ? Math.min(...values) : null
  const maxVal = values.length > 0 ? Math.max(...values) : null

  return (
    <SectionCard
      title="Glucose Telemetry Timeline"
      subtitle="Rolling chronological sensor history from patient CGM buffer"
      badge={<ProvenanceBadge label="OBSERVED" />}
    >
      {readings.length === 0 ? (
        <div className="flex h-36 flex-col items-center justify-center rounded-lg border border-dashed border-border text-center">
          <p className="text-xs font-medium text-text-primary">No continuous glucose history recorded</p>
          <p className="mt-1 text-[11px] text-text-muted">
            Wearable telemetry ingestion automatically populates this chronological timeline.
          </p>
        </div>
      ) : (
        <div className="space-y-4">
          {/* Summary metrics header */}
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <MetricTile
              label="Latest Observed"
              value={latest ? `${latest.value.toFixed(1)} mmol/L` : '—'}
              highlight
            />
            <MetricTile
              label="Session Minimum"
              value={minVal != null ? `${minVal.toFixed(1)} mmol/L` : '—'}
            />
            <MetricTile
              label="Session Maximum"
              value={maxVal != null ? `${maxVal.toFixed(1)} mmol/L` : '—'}
            />
            <MetricTile
              label="Total Telemetry Points"
              value={readings.length.toString()}
              unit="readings"
            />
          </div>

          {/* Line Chart */}
          <div className="h-64 w-full" aria-label="Glucose history chart" role="img">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chartPoints} margin={{ top: 10, right: 10, left: -16, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#2A3140" vertical={false} />
                <XAxis
                  dataKey="time"
                  tick={{ fill: '#94A3B8', fontSize: 11 }}
                  axisLine={{ stroke: '#2A3140' }}
                  tickLine={false}
                />
                <YAxis
                  domain={['auto', 'auto']}
                  tick={{ fill: '#94A3B8', fontSize: 11 }}
                  axisLine={false}
                  tickLine={false}
                  tickFormatter={(v: number) => v.toFixed(1)}
                  unit=" mmol"
                />
                <Tooltip
                  contentStyle={{
                    backgroundColor: '#151922',
                    border: '1px solid #2A3140',
                    borderRadius: '8px',
                    fontSize: '12px',
                    color: '#F1F5F9',
                  }}
                  formatter={(value: number) => [
                    `${value.toFixed(1)} mmol/L (${(value * 18.018).toFixed(0)} mg/dL)`,
                    'Observed Glucose',
                  ]}
                  labelFormatter={(_: string, payload: { payload?: ChartPoint }[]) => {
                    const iso = payload?.[0]?.payload?.isoTime
                    return iso ? fmtDateTime(iso) : ''
                  }}
                />
                <Line
                  type="monotone"
                  dataKey="value"
                  stroke="#4F8CFF"
                  strokeWidth={2.5}
                  dot={<Dot r={3} fill="#4F8CFF" stroke="#151922" strokeWidth={1} />}
                  activeDot={{ r: 5, fill: '#4F8CFF' }}
                  isAnimationActive={false}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
          <p className="text-[10px] text-text-muted">
            Axis displays observed values only. Simulated or forecasted points are never plotted on this curve.
          </p>
        </div>
      )}
    </SectionCard>
  )
}

// ── ROW 4: Prediction Dashboard ──────────────────────────────────────────────

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
      // handled in mutation state
    }
  }

  return (
    <div className="flex flex-col items-end gap-1.5">
      <button
        onClick={() => void handleClick()}
        disabled={mutation.isPending}
        className="btn-primary text-xs"
        aria-label="Generate a new 2-hour glucose spike prediction"
      >
        {mutation.isPending && (
          <span
            className="inline-block h-3.5 w-3.5 animate-spin rounded-full border-2 border-white border-t-transparent"
            aria-hidden="true"
          />
        )}
        {mutation.isPending ? 'Generating Inference…' : '⚡ Generate Prediction'}
      </button>
      {mutation.isError && (
        <p className="text-[11px] text-status-danger" role="alert">
          {mutation.error instanceof Error
            ? mutation.error.message
            : 'Failed to generate prediction'}
        </p>
      )}
    </div>
  )
}

function PredictionDashboard({
  patientId,
  onSelectPrediction,
  selectedPrediction,
}: {
  patientId: string
  onSelectPrediction: (prediction: Prediction | null) => void
  selectedPrediction?: Prediction | null
}) {
  const [latestPredictionId, setLatestPredictionId] = useState<string | null>(null)
  const latestPrediction = usePrediction(latestPredictionId ?? undefined)

  const history = usePatientPredictions(patientId, 10)
  const historyLatest = history.data?.content?.[0]

  const activePrediction =
    latestPrediction.data ?? selectedPrediction ?? (historyLatest?.status === 'COMPLETED' ? historyLatest : undefined)

  const prob = activePrediction?.spikeProbability
  const ci = activePrediction?.confidenceInterval
  const isCiUncalibrated = ci && ci.low <= 0.001 && ci.high >= 0.999

  return (
    <div id="prediction-dashboard" className="space-y-4">
      <SectionCard
        title="2-Hour Glucose Spike Prediction Dashboard"
        subtitle="Forward prognostic modeling of postprandial glucose spike probability (≥ 10.0 mmol/L)"
        badge={<ProvenanceBadge label="PREDICTED" />}
        action={
          <GeneratePredictionButton
            patientId={patientId}
            onCreated={(id) => {
              setLatestPredictionId(id)
            }}
          />
        }
      >
        {activePrediction ? (
          <div className="space-y-6">
            {/* Primary Prediction Stats */}
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <div className="card p-4 border-accent/40 bg-accent/5">
                <p className="text-[10px] font-medium uppercase tracking-wider text-text-muted">
                  Spike Probability
                </p>
                <div className="mt-1 flex items-baseline gap-1">
                  <span className="text-3xl font-extrabold tabular-nums text-text-primary">
                    {prob != null ? (prob * 100).toFixed(1) : '—'}
                  </span>
                  <span className="text-lg text-text-muted">%</span>
                </div>
              </div>

              <div className="card p-4">
                <p className="text-[10px] font-medium uppercase tracking-wider text-text-muted">
                  Risk Category
                </p>
                <div className="mt-2">
                  {activePrediction.riskCategory ? (
                    <StatusBadge status={activePrediction.riskCategory} />
                  ) : (
                    <span className="text-xs text-text-muted">—</span>
                  )}
                </div>
              </div>

              <div className="card p-4">
                <p className="text-[10px] font-medium uppercase tracking-wider text-text-muted">
                  Confidence Interval (95%)
                </p>
                <p className="mt-2 font-mono text-xs text-text-primary">
                  {ci
                    ? `[${(ci.low * 100).toFixed(1)}% – ${(ci.high * 100).toFixed(1)}%]`
                    : '—'}
                </p>
                {isCiUncalibrated && (
                  <p className="mt-1 text-[10px] text-text-muted">
                    Default uncalibrated range
                  </p>
                )}
              </div>

              <div className="card p-4">
                <p className="text-[10px] font-medium uppercase tracking-wider text-text-muted">
                  Model & Horizon
                </p>
                <p className="mt-1 text-xs font-mono text-text-primary">
                  {activePrediction.modelVersion}
                </p>
                <p className="text-[10px] text-text-muted mt-0.5">
                  Horizon: {activePrediction.predictionHorizonHours}h · Twin v{activePrediction.twinStateVersion}
                </p>
              </div>
            </div>

            {/* Prediction History Table */}
            <div>
              <div className="mb-2 flex items-center justify-between">
                <h4 className="text-xs font-semibold uppercase tracking-wider text-text-muted">
                  Historical Prediction Runs
                </h4>
                {history.data && (
                  <span className="text-[11px] text-text-muted">
                    {history.data.content.length} records logged
                  </span>
                )}
              </div>

              {history.isLoading ? (
                <LoadingSkeleton rows={2} height="h-10" />
              ) : history.data && history.data.content.length > 0 ? (
                <div className="overflow-x-auto rounded-lg border border-border">
                  <table className="w-full text-left text-xs" aria-label="Prediction History">
                    <thead>
                      <tr className="border-b border-border bg-bg-card text-text-muted font-medium">
                        <th className="py-2.5 px-3">Timestamp</th>
                        <th className="py-2.5 px-3">Probability</th>
                        <th className="py-2.5 px-3">Risk Assessment</th>
                        <th className="py-2.5 px-3">Model</th>
                        <th className="py-2.5 px-3 text-right">Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border/60">
                      {history.data.content.map((p) => (
                        <tr
                          key={p.predictionId}
                          onClick={() => onSelectPrediction(p)}
                          className="hover:bg-bg-elevated/40 cursor-pointer transition-colors"
                        >
                          <td className="py-2 px-3 text-text-secondary font-mono text-[11px]">
                            {fmtDateTime(p.predictedAt)}
                          </td>
                          <td className="py-2 px-3 font-semibold text-text-primary tabular-nums">
                            {p.spikeProbability != null ? `${(p.spikeProbability * 100).toFixed(1)}%` : '—'}
                          </td>
                          <td className="py-2 px-3">
                            {p.riskCategory ? (
                              <StatusBadge status={p.riskCategory} />
                            ) : (
                              <span className="text-text-muted">—</span>
                            )}
                          </td>
                          <td className="py-2 px-3 font-mono text-[10px] text-text-muted">
                            {p.modelVersion}
                          </td>
                          <td className="py-2 px-3 text-right">
                            <StatusBadge status={p.status} />
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <p className="text-xs text-text-muted py-2">No prior prediction records found.</p>
              )}
            </div>
          </div>
        ) : (
          <div className="p-8 text-center">
            <p className="text-xs text-text-muted">
              No prediction generated yet. Click <strong className="text-text-primary">⚡ Generate Prediction</strong> above to execute the model.
            </p>
          </div>
        )}
      </SectionCard>
    </div>
  )
}

// ── ROW 5: Explainability (SHAP Contributing Factors) ─────────────────────────

function ExplainabilitySection({ factors }: { factors: ContributingFactor[] }) {
  const maxVal = Math.max(...factors.map((f) => f.contribution), 0.01)

  return (
    <SectionCard
      title="Machine Learning Explainability (SHAP Attributions)"
      subtitle="Relative feature attribution for model decision"
      badge={<ProvenanceBadge label="PREDICTED" />}
    >
      <div className="space-y-4">
        <div className="rounded-lg border border-border bg-bg-card p-3 text-[11px] text-text-secondary leading-relaxed">
          <strong className="text-text-primary mr-1">Clinical Interpretability Note:</strong>
          These SHAP values quantify the statistical contribution of each biometric signal toward the model's spike risk score. They reflect algorithm correlation in the training distribution — they do not establish direct medical causality.
        </div>

        {factors.length === 0 ? (
          <p className="text-xs text-text-muted py-4 text-center">
            No SHAP factor attributions provided by the model for this prediction run.
          </p>
        ) : (
          <div className="space-y-3">
            {factors.map((factor) => {
              const isIncrease = factor.direction === 'INCREASES_RISK'
              const percentage = (factor.contribution * 100).toFixed(1)
              const barWidth = Math.min(100, Math.max(5, (factor.contribution / maxVal) * 100))

              return (
                <div key={factor.factorName} className="space-y-1">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-mono text-text-primary font-medium">
                      {factor.factorName}
                    </span>
                    <span
                      className={`font-semibold tabular-nums ${
                        isIncrease ? 'text-status-danger' : 'text-status-success'
                      }`}
                    >
                      {isIncrease ? '↑ +' : '↓ -'}{percentage}% {isIncrease ? '(Increases Risk)' : '(Decreases Risk)'}
                    </span>
                  </div>
                  <div className="h-2 w-full overflow-hidden rounded-full bg-bg-card border border-border">
                    <div
                      className={`h-full rounded-full transition-all duration-300 ${
                        isIncrease ? 'bg-status-danger' : 'bg-status-success'
                      }`}
                      style={{ width: `${barWidth}%` }}
                    />
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </SectionCard>
  )
}

// ── MAIN PATIENT DETAIL PAGE ─────────────────────────────────────────────────

export default function PatientDetailPage() {
  const { patientId } = useParams<{ patientId: string }>()
  const queryClient = useQueryClient()

  const [selectedPrediction, setSelectedPrediction] = useState<Prediction | null>(null)

  const ehr = usePatientEhr(patientId)
  const twin = usePatientTwinState(patientId)
  const predictions = usePatientPredictions(patientId, 1)

  const isFetching = ehr.isFetching || twin.isFetching

  function handleRefresh() {
    void queryClient.invalidateQueries({ queryKey: ['patients', patientId] })
  }

  const twinNotFound =
    twin.isError && twin.error instanceof ApiError && twin.error.status === 404

  // Determine active prediction for explainability section
  const currentPrediction = selectedPrediction ?? predictions.data?.content?.[0]

  return (
    <div className="space-y-8">
      {/* ── HEADER ── */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-border pb-4">
        <div className="flex items-center gap-3 min-w-0">
          <Link
            to="/patients"
            className="flex h-8 w-8 items-center justify-center rounded-lg border border-border bg-bg-card text-text-muted hover:border-border-hover hover:text-text-primary transition-colors flex-shrink-0"
            title="Back to Patient Registry"
          >
            <IconArrowLeft size={16} />
          </Link>

          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-bold tracking-tight text-text-primary truncate">
                {patientId}
              </h2>
              {twin.data && <StatusBadge status={twin.data.status} />}
            </div>
            {twin.data && (
              <p className="text-[11px] text-text-muted mt-0.5">
                Digital Twin v{twin.data.twinVersion} · Synchronized {fmtDateTime(twin.data.lastUpdatedAt)}
              </p>
            )}
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={handleRefresh}
            disabled={isFetching}
            className="btn-secondary text-xs"
            aria-label="Refresh patient data"
          >
            <IconRefresh size={14} className={isFetching ? 'animate-spin' : ''} />
            Refresh State
          </button>
        </div>
      </div>

      {/* Patient Not Found State */}
      {twinNotFound && (
        <div className="card p-12 text-center border-status-danger/30">
          <IconTwin size={32} className="mx-auto text-text-muted mb-3" />
          <h3 className="text-sm font-semibold text-text-primary">Patient Record Not Found</h3>
          <p className="mt-1 text-xs text-text-secondary">
            No active physiological twin registered for UUID: <span className="font-mono text-accent">{patientId}</span>
          </p>
          <Link to="/patients" className="btn-primary mt-4 text-xs">
            Return to Registry
          </Link>
        </div>
      )}

      {!twinNotFound && (
        <>
          {/* ── ROW 1: PATIENT OVERVIEW (EHR, Twin, Current Glucose) ── */}
          <section id="digital-twin-overview" className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {ehr.isLoading ? (
              <LoadingSkeleton rows={3} height="h-36" />
            ) : ehr.data ? (
              <EhrCard ehr={ehr.data} />
            ) : (
              <SectionCard title="EHR Summary" badge={<ProvenanceBadge label="OBSERVED" />}>
                <p className="text-xs text-text-muted py-6">No clinical EHR record uploaded for this patient.</p>
              </SectionCard>
            )}

            {twin.isLoading ? (
              <LoadingSkeleton rows={3} height="h-36" />
            ) : twin.data ? (
              <TwinMetaCard twin={twin.data} />
            ) : null}

            {twin.isLoading ? (
              <LoadingSkeleton rows={3} height="h-36" />
            ) : twin.data?.dynamicLayer ? (
              <GlucoseCard dl={twin.data.dynamicLayer} />
            ) : (
              <SectionCard title="Current Glucose Telemetry" badge={<ProvenanceBadge label="OBSERVED" />}>
                <p className="text-xs text-text-muted py-6">
                  Twin is <span className="font-semibold text-text-primary">INITIALISED</span>. Ingest wearable telemetry to activate live glucose monitoring.
                </p>
              </SectionCard>
            )}
          </section>

          {/* ── ROW 2: WEARABLE MONITORING (Vitals Grid) ── */}
          {twin.data?.dynamicLayer && (
            <WearableMonitoringSection dl={twin.data.dynamicLayer} />
          )}

          {/* ── ROW 3: GLUCOSE HISTORY (Full-Width Recharts Chart) ── */}
          {patientId && (
            <div id="glucose-history">
              <CgmHistorySection patientId={patientId} />
            </div>
          )}

          {/* ── ROW 4: PREDICTION DASHBOARD ── */}
          {patientId && (
            <PredictionDashboard
              patientId={patientId}
              onSelectPrediction={setSelectedPrediction}
              selectedPrediction={selectedPrediction}
            />
          )}

          {/* ── ROW 5: EXPLAINABILITY (SHAP Contributing Factors) ── */}
          {currentPrediction?.topContributingFactors && currentPrediction.topContributingFactors.length > 0 && (
            <div id="explainability">
              <ExplainabilitySection factors={currentPrediction.topContributingFactors} />
            </div>
          )}

          {/* ── ROW 6: CLINICAL INSIGHTS ── */}
          {patientId && (
            <div id="clinical-insights">
              <ClinicalInsightPanel patientId={patientId} />
            </div>
          )}

          {/* ── ROW 7: WHAT-IF SIMULATION ── */}
          {patientId && (
            <div id="what-if-simulation">
              <WhatIfSimulationPanel patientId={patientId} />
            </div>
          )}
        </>
      )}
    </div>
  )
}
