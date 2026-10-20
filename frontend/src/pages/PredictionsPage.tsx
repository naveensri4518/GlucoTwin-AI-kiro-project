import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { usePatients } from '@/hooks/usePatients'
import {
  usePatientPredictions,
  usePrediction,
  useCreatePrediction,
} from '@/hooks/usePredictions'
import type { Prediction, ContributingFactor } from '@/types/prediction'
import { PatientContextSelector } from '@/components/common/PatientContextSelector'
import { SectionCard } from '@/components/common/SectionCard'
import { StatusBadge } from '@/components/common/StatusBadge'
import { ProvenanceBadge } from '@/components/common/ProvenanceBadge'
import { LoadingSkeleton } from '@/components/common/LoadingSkeleton'
import { EmptyState } from '@/components/common/EmptyState'
import { IconBrain, IconRefresh } from '@/components/common/Icons'

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

export default function PredictionsPage() {
  const [searchParams] = useSearchParams()
  const { data: patientsData } = usePatients()
  const patients = patientsData?.content ?? []

  const activePatientId =
    searchParams.get('patientId') || patients[0]?.patientId || ''

  const [latestPredictionId, setLatestPredictionId] = useState<string | null>(null)
  const [selectedHistorical, setSelectedHistorical] = useState<Prediction | null>(null)

  const livePrediction = usePrediction(latestPredictionId ?? undefined)
  const historyQuery = usePatientPredictions(activePatientId, 15)
  const createMutation = useCreatePrediction(activePatientId)

  const historyList = historyQuery.data?.content ?? []
  const activePrediction =
    selectedHistorical ??
    livePrediction.data ??
    historyList[0] ??
    null

  async function handleTrigger() {
    try {
      const resp = await createMutation.mutateAsync()
      setSelectedHistorical(null)
      setLatestPredictionId(resp.predictionId)
    } catch {
      // handled in mutation state
    }
  }

  const prob = activePrediction?.spikeProbability
  const ci = activePrediction?.confidenceInterval
  const isCiUncalibrated = ci && ci.low <= 0.001 && ci.high >= 0.999
  const factors = activePrediction?.topContributingFactors ?? []
  const maxFactorVal = Math.max(...factors.map((f: ContributingFactor) => f.contribution), 0.01)

  return (
    <div className="space-y-6">
      {/* Module Title */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-border pb-4">
        <div>
          <div className="flex items-center gap-2.5">
            <h2 className="text-xl font-bold tracking-tight text-text-primary">
              2-Hour Glucose Spike Predictions
            </h2>
            <ProvenanceBadge label="PREDICTED" />
          </div>
          <p className="mt-1 text-xs text-text-secondary">
            Forward prognostic machine-learning inference evaluating postprandial glucose spike probability (≥ 10.0 mmol/L).
          </p>
        </div>

        <button
          onClick={() => void historyQuery.refetch()}
          className="btn-secondary text-xs"
          aria-label="Refresh prediction history"
        >
          <IconRefresh size={14} className={historyQuery.isFetching ? 'animate-spin' : ''} />
          Refresh
        </button>
      </div>

      {/* Patient Context Switcher */}
      <PatientContextSelector currentPatientId={activePatientId} />

      {/* Trigger Inference & Forecast Summary */}
      <div className="grid gap-6 lg:grid-cols-3">
        {/* Main Probability Panel */}
        <div className="lg:col-span-2 space-y-6">
          <SectionCard
            title="Active Model Forecast"
            subtitle={
              activePrediction
                ? `Prediction ID: ${activePrediction.predictionId}`
                : 'Awaiting model inference execution'
            }
            action={
              <button
                onClick={() => void handleTrigger()}
                disabled={createMutation.isPending || !activePatientId}
                className="btn-primary text-xs"
                aria-label="Run 2-hour spike prediction"
              >
                <IconBrain size={14} />
                {createMutation.isPending ? 'Generating Model Inference…' : 'Run 2-Hr Spike Prediction'}
              </button>
            }
          >
            {createMutation.isError && (
              <div className="mb-4 rounded border border-status-danger/40 bg-status-danger/10 p-3 text-status-danger text-xs">
                {createMutation.error instanceof Error
                  ? createMutation.error.message
                  : 'Prediction inference failed. Ensure wearable telemetry exists.'}
              </div>
            )}

            {historyQuery.isLoading ? (
              <LoadingSkeleton rows={2} height="h-28" />
            ) : activePrediction ? (
              <div className="space-y-6">
                <div className="grid gap-4 sm:grid-cols-3">
                  <div className="card p-4 border-border bg-card">
                    <p className="text-[10px] font-mono uppercase tracking-wider text-text-muted">
                      Spike Probability
                    </p>
                    <div className="mt-2 flex items-baseline gap-1">
                      <span className="text-4xl font-extrabold tabular-nums tracking-tight text-text-primary">
                        {prob != null ? (prob * 100).toFixed(1) : '—'}
                      </span>
                      <span className="text-lg text-text-muted">%</span>
                    </div>
                  </div>

                  <div className="card p-4 border-border bg-card">
                    <p className="text-[10px] font-mono uppercase tracking-wider text-text-muted">
                      Risk Stratification
                    </p>
                    <div className="mt-3">
                      {activePrediction.riskCategory ? (
                        <StatusBadge status={activePrediction.riskCategory} />
                      ) : (
                        <span className="text-xs text-text-muted">—</span>
                      )}
                    </div>
                  </div>

                  <div className="card p-4 border-border bg-card">
                    <p className="text-[10px] font-mono uppercase tracking-wider text-text-muted">
                      95% Confidence Interval
                    </p>
                    <p className="mt-2 font-mono text-xs font-semibold text-text-primary">
                      {ci
                        ? `[${(ci.low * 100).toFixed(1)}% – ${(ci.high * 100).toFixed(1)}%]`
                        : '—'}
                    </p>
                    {isCiUncalibrated && (
                      <p className="mt-1 text-[10px] text-text-muted">
                        (Default uncalibrated range)
                      </p>
                    )}
                  </div>
                </div>

                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-[11px] border-t border-border pt-3 text-text-muted">
                  <div>
                    <span className="block text-[10px] uppercase font-mono">Horizon</span>
                    <span className="text-text-primary font-medium">{activePrediction.predictionHorizonHours} Hours</span>
                  </div>
                  <div>
                    <span className="block text-[10px] uppercase font-mono">Model</span>
                    <span className="text-text-primary font-mono">{activePrediction.modelVersion}</span>
                  </div>
                  <div>
                    <span className="block text-[10px] uppercase font-mono">Twin State</span>
                    <span className="text-text-primary font-medium">v{activePrediction.twinStateVersion}</span>
                  </div>
                  <div>
                    <span className="block text-[10px] uppercase font-mono">Timestamp</span>
                    <span className="text-text-primary font-mono">{fmtDateTime(activePrediction.predictedAt)}</span>
                  </div>
                </div>
              </div>
            ) : (
              <EmptyState
                title="No Predictions Generated Yet"
                description="Click 'Run 2-Hr Spike Prediction' above to invoke the trained XGBoost model on this patient's digital twin state."
                icon={<IconBrain size={24} />}
              />
            )}
          </SectionCard>

          {/* Machine Learning Explainability (SHAP Attributions) */}
          <SectionCard
            title="SHAP Feature Explanations"
            subtitle="Statistical attribution of input signals to prediction output"
          >
            {factors.length === 0 ? (
              <p className="text-xs text-text-muted py-4 text-center">
                Feature attributions will display here once a prediction is executed.
              </p>
            ) : (
              <div className="space-y-4">
                <div className="rounded border border-border bg-bg-elevated/40 p-3 text-[11px] text-text-secondary leading-relaxed">
                  <strong className="text-text-primary">Interpretability Disclosure:</strong> These values reflect mathematical SHAP attributions from the XGBoost gradient-boosted decision trees. They quantify model feature importance for this sample and do not imply proven medical causation.
                </div>

                <div className="space-y-3">
                  {factors.map((factor: ContributingFactor) => {
                    const isIncrease = factor.direction === 'INCREASES_RISK'
                    const percentage = (factor.contribution * 100).toFixed(1)
                    const widthPct = Math.min(100, Math.max(8, (factor.contribution / maxFactorVal) * 100))

                    return (
                      <div key={factor.factorName} className="space-y-1">
                        <div className="flex items-center justify-between text-xs">
                          <span className="font-mono text-text-primary">{factor.factorName}</span>
                          <span
                            className={`font-mono text-[11px] font-semibold tabular-nums ${
                              isIncrease ? 'text-status-danger' : 'text-status-success'
                            }`}
                          >
                            {isIncrease ? '↑ +' : '↓ -'}{percentage}% {isIncrease ? '(Increases Risk)' : '(Decreases Risk)'}
                          </span>
                        </div>
                        <div className="h-1.5 w-full overflow-hidden rounded-full bg-border">
                          <div
                            className={`h-full rounded-full transition-all duration-300 ${
                              isIncrease ? 'bg-status-danger' : 'bg-status-success'
                            }`}
                            style={{ width: `${widthPct}%` }}
                          />
                        </div>
                      </div>
                    )
                  })}
                </div>
              </div>
            )}
          </SectionCard>
        </div>

        {/* Prediction History Log */}
        <div>
          <SectionCard
            title="Prediction History"
            subtitle="Verified historical inference runs"
          >
            {historyQuery.isLoading ? (
              <LoadingSkeleton rows={4} height="h-12" />
            ) : historyList.length === 0 ? (
              <p className="text-xs text-text-muted py-6 text-center">No predictions logged for this patient.</p>
            ) : (
              <div className="space-y-2">
                {historyList.map((p: Prediction) => {
                  const isSelected = activePrediction?.predictionId === p.predictionId
                  return (
                    <div
                      key={p.predictionId}
                      onClick={() => setSelectedHistorical(p)}
                      className={`card p-3 cursor-pointer transition-all ${
                        isSelected
                          ? 'border-text-secondary bg-elevated'
                          : 'hover:border-border-hover'
                      }`}
                    >
                      <div className="flex items-center justify-between">
                        <span className="font-mono text-xs font-semibold text-text-primary">
                          {p.spikeProbability != null ? `${(p.spikeProbability * 100).toFixed(1)}%` : '—'}
                        </span>
                        {p.riskCategory && <StatusBadge status={p.riskCategory} />}
                      </div>
                      <div className="mt-1 flex items-center justify-between text-[10px] text-text-muted">
                        <span>{fmtDateTime(p.predictedAt)}</span>
                        <StatusBadge status={p.status} />
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </SectionCard>
        </div>
      </div>
    </div>
  )
}
