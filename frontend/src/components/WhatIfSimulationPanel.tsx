/**
 * Phase 7B — What-If Simulation Panel
 *
 * Renders a scenario input form and displays the real simulation result returned
 * by the backend XGBoost pipeline. No spike probabilities are computed,
 * fabricated, or hardcoded here — the backend is the sole source of truth.
 *
 * Output is always labelled SIMULATED (never OBSERVED or PREDICTED).
 * The backend-provided safety disclaimer is always displayed verbatim.
 */

import { useState } from 'react'
import { useRunSimulation } from '@/hooks/useSimulation'
import { ApiError } from '@/lib/apiClient'
import type { RiskCategory } from '@/types/prediction'
import type {
  SimulationFormState,
  SimulationResponse,
  SimulationContributingFactor,
} from '@/types/simulation'
import {
  SIMULATION_FORM_DEFAULTS,
  ACTIVITY_LEVEL_OPTIONS,
} from '@/types/simulation'

// ── Helpers ───────────────────────────────────────────────────────────────────

function fmtDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  try {
    return new Intl.DateTimeFormat('en-GB', {
      day: '2-digit', month: 'short', year: 'numeric',
      hour: '2-digit', minute: '2-digit', timeZoneName: 'short',
    }).format(new Date(iso))
  } catch { return iso }
}

function fmtPercent(val: number): string {
  return `${(val * 100).toFixed(1)}%`
}

// ── Shared primitives (match PatientDetailPage style) ─────────────────────────

function SimulatedBadge() {
  return (
    <span className="ml-1.5 rounded border border-violet-500/40 px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide text-violet-400">
      SIMULATED
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

// ── Simulation safety disclaimer ──────────────────────────────────────────────

function SimulationSafetyDisclaimer({ text }: { text?: string }) {
  return (
    <p className="mt-4 rounded border border-violet-500/20 bg-violet-950/20 px-3 py-2 text-[10px] text-slate-400">
      <strong className="text-violet-300">⚠ Hypothetical scenario.</strong>{' '}
      {text ?? 'This is a what-if simulation. Not a medical recommendation.'}
    </p>
  )
}

// ── Contributing factors ──────────────────────────────────────────────────────

function SimContributingFactors({ factors }: { factors: SimulationContributingFactor[] }) {
  if (factors.length === 0) return null
  const maxContrib = Math.max(...factors.map((f) => f.contribution), 0.01)

  return (
    <div className="mt-4 border-t border-surface-border pt-4">
      <p className="mb-3 text-[11px] font-semibold uppercase tracking-wider text-slate-400">
        Top Contributing Factors
      </p>
      <p className="mb-3 text-[10px] text-slate-600">
        Model feature contributions for this hypothetical scenario. Statistical
        drivers only — not medical diagnoses or treatment recommendations.
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
            <div className="h-1 w-full overflow-hidden rounded-full bg-surface" role="presentation">
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

// ── Scenario inputs echo ──────────────────────────────────────────────────────

function ScenarioInputsCard({ inputs }: { inputs: Record<string, unknown> }) {
  if (Object.keys(inputs).length === 0) return null
  return (
    <SectionCard title="Scenario Inputs" badge={<SimulatedBadge />}>
      {Object.entries(inputs).map(([key, val]) => (
        <Row key={key} label={key} value={String(val)} />
      ))}
    </SectionCard>
  )
}

// ── Delta vs baseline ─────────────────────────────────────────────────────────

function DeltaDisplay({ delta }: { delta: number | null | undefined }) {
  if (delta == null) {
    return (
      <div className="rounded border border-surface-border bg-surface px-3 py-2">
        <p className="text-[10px] text-slate-500">Delta vs baseline</p>
        <p className="mt-0.5 text-xs text-slate-500 italic">Baseline prediction unavailable</p>
      </div>
    )
  }

  const isPositive = delta > 0
  const isNeutral  = delta === 0

  return (
    <div className="rounded border border-surface-border bg-surface px-3 py-2">
      <p className="text-[10px] text-slate-500">Delta vs baseline</p>
      <p
        className={`mt-0.5 text-lg font-bold tabular-nums ${
          isNeutral   ? 'text-slate-400'
          : isPositive ? 'text-risk-high'
          : 'text-risk-low'
        }`}
      >
        {isPositive ? '+' : ''}{fmtPercent(delta)}
      </p>
      <p className="mt-0.5 text-[10px] text-slate-600">
        {isNeutral
          ? 'No change vs baseline'
          : isPositive
          ? 'Higher spike risk than baseline'
          : 'Lower spike risk than baseline'}
      </p>
    </div>
  )
}

// ── Data quality warnings ─────────────────────────────────────────────────────

function SimDataQualityWarnings({ warnings }: { warnings: string[] }) {
  if (warnings.length === 0) return null
  return (
    <div
      className="rounded border border-risk-moderate/40 bg-risk-moderate/5 p-3"
      role="alert"
      aria-live="polite"
    >
      <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-wider text-risk-moderate">
        ⚠ Data Quality Warnings
      </p>
      <ul className="flex flex-col gap-1" role="list">
        {warnings.map((w) => (
          <li key={w} className="text-[11px] text-slate-400">• {w}</li>
        ))}
      </ul>
    </div>
  )
}

// ── Simulation result card ────────────────────────────────────────────────────

function SimulationResultCard({ result }: { result: SimulationResponse }) {
  const ci = result.confidenceInterval

  return (
    <div className="mt-6 flex flex-col gap-4">
      {/* Main result card */}
      <SectionCard
        title="Simulation Result"
        badge={<SimulatedBadge />}
        className="col-span-full"
      >
        {/* Spike probability hero */}
        <div className="mb-4 flex flex-wrap items-center gap-6">
          <div>
            <p className="text-[10px] text-slate-500">Simulated spike probability</p>
            <p className="mt-0.5 text-4xl font-bold tabular-nums text-slate-100">
              {fmtPercent(result.spikeProbability).replace('%', '')}
              <span className="text-xl text-slate-400">%</span>
            </p>
          </div>
          <div>
            <p className="text-[10px] text-slate-500">Risk category</p>
            <div className="mt-1">
              <RiskBadge category={result.riskCategory} />
            </div>
          </div>
          <div>
            <p className="text-[10px] text-slate-500">95% confidence interval</p>
            <p className="mt-0.5 font-mono text-xs text-slate-300">
              [{fmtPercent(ci.low)} – {fmtPercent(ci.high)}]
            </p>
          </div>
        </div>

        {/* Delta + meta tiles */}
        <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
          <DeltaDisplay delta={result.deltaVsBaseline} />
          <div className="rounded border border-surface-border bg-surface px-3 py-2">
            <p className="text-[10px] text-slate-500">Horizon</p>
            <p className="mt-0.5 text-sm font-semibold text-slate-200">
              {result.predictionHorizonHours}h
            </p>
          </div>
          <div className="rounded border border-surface-border bg-surface px-3 py-2">
            <p className="text-[10px] text-slate-500">Model version</p>
            <p className="mt-0.5 font-mono text-xs text-slate-300">{result.modelVersion}</p>
          </div>
          <div className="rounded border border-surface-border bg-surface px-3 py-2">
            <p className="text-[10px] text-slate-500">Twin state v</p>
            <p className="mt-0.5 text-sm font-semibold text-slate-200">
              v{result.twinStateVersion}
            </p>
          </div>
        </div>

        {/* Meta row */}
        <div className="mb-4 flex flex-wrap gap-x-5 gap-y-1 border-t border-surface-border pt-3 text-[11px] text-slate-500">
          <span>
            Simulated at:{' '}
            <time dateTime={result.simulatedAt}>{fmtDateTime(result.simulatedAt)}</time>
          </span>
          <span className="font-mono">ID: {result.simulationId}</span>
        </div>

        {/* Data quality warnings */}
        {result.dataQualityWarnings.length > 0 && (
          <div className="mb-4">
            <SimDataQualityWarnings warnings={result.dataQualityWarnings} />
          </div>
        )}

        {/* Contributing factors */}
        {result.topContributingFactors.length > 0 && (
          <SimContributingFactors factors={result.topContributingFactors} />
        )}

        {/* Safety disclaimer — always shown, always verbatim from backend */}
        <SimulationSafetyDisclaimer text={result.disclaimer} />
      </SectionCard>

      {/* Scenario inputs echo */}
      <ScenarioInputsCard inputs={result.scenarioInputs} />
    </div>
  )
}

// ── Form validation ───────────────────────────────────────────────────────────

interface FormErrors {
  mealCarbsGrams?: string
  activityLevel?: string
  atLeastOne?: string
}

function validateForm(form: SimulationFormState): FormErrors {
  const errors: FormErrors = {}

  if (form.mealCarbsGrams !== '') {
    const val = parseFloat(form.mealCarbsGrams)
    if (isNaN(val)) {
      errors.mealCarbsGrams = 'Must be a valid number'
    } else if (val < 0) {
      errors.mealCarbsGrams = 'Must be 0 or greater'
    } else if (val > 500) {
      errors.mealCarbsGrams = 'Must be 500 g or less'
    }
  }

  // At least one field must be provided (mirrors backend validation)
  const hasCarbs      = form.mealCarbsGrams !== '' && !errors.mealCarbsGrams
  const hasActivity   = form.activityLevel !== ''
  const hasMedication = form.medicationTaken // true counts as provided

  if (!hasCarbs && !hasActivity && !hasMedication) {
    errors.atLeastOne = 'Provide at least one scenario parameter'
  }

  return errors
}

function hasErrors(errors: FormErrors): boolean {
  return Object.keys(errors).length > 0
}

// ── What-If Simulation Panel ──────────────────────────────────────────────────

interface WhatIfSimulationPanelProps {
  patientId: string
}

export function WhatIfSimulationPanel({ patientId }: WhatIfSimulationPanelProps) {
  const [form, setForm] = useState<SimulationFormState>(SIMULATION_FORM_DEFAULTS)
  const [formErrors, setFormErrors] = useState<FormErrors>({})
  const [lastResult, setLastResult] = useState<SimulationResponse | null>(null)

  const simulation = useRunSimulation(patientId)

  function handleCarbsChange(e: React.ChangeEvent<HTMLInputElement>) {
    const val = e.target.value
    setForm((prev) => ({ ...prev, mealCarbsGrams: val }))
    // Clear field error on change
    if (formErrors.mealCarbsGrams || formErrors.atLeastOne) {
      setFormErrors((prev) => {
        const next = { ...prev }
        delete next.mealCarbsGrams
        delete next.atLeastOne
        return next
      })
    }
  }

  function handleActivityChange(e: React.ChangeEvent<HTMLSelectElement>) {
    const val = e.target.value
    setForm((prev) => ({
      ...prev,
      activityLevel: val as SimulationFormState['activityLevel'],
    }))
    if (formErrors.atLeastOne) {
      setFormErrors((prev) => { const n = { ...prev }; delete n.atLeastOne; return n })
    }
  }

  function handleMedicationChange(e: React.ChangeEvent<HTMLInputElement>) {
    setForm((prev) => ({ ...prev, medicationTaken: e.target.checked }))
    if (formErrors.atLeastOne) {
      setFormErrors((prev) => { const n = { ...prev }; delete n.atLeastOne; return n })
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()

    const errors = validateForm(form)
    if (hasErrors(errors)) {
      setFormErrors(errors)
      return
    }

    setFormErrors({})

    // Build request — only include non-empty fields
    const body: import('@/types/simulation').SimulationRequest = {}
    if (form.mealCarbsGrams !== '') {
      body.mealCarbsGrams = parseFloat(form.mealCarbsGrams)
    }
    if (form.activityLevel !== '') {
      body.activityLevel = form.activityLevel
    }
    if (form.medicationTaken) {
      body.medicationTaken = true
    }

    try {
      const result = await simulation.mutateAsync(body)
      setLastResult(result)
    } catch {
      // Error displayed via simulation.isError below
    }
  }

  function handleReset() {
    setForm(SIMULATION_FORM_DEFAULTS)
    setFormErrors({})
    setLastResult(null)
    simulation.reset()
  }

  // Derive a user-friendly error message
  function getApiErrorMessage(): string {
    const err = simulation.error
    if (!err) return 'Simulation failed'
    if (err instanceof ApiError) {
      // Map known backend error codes to friendly messages
      switch (err.status) {
        case 400: return `Invalid input: ${err.message}`
        case 404: return 'Patient or Digital Twin not found'
        case 409: return 'Twin is archived — cannot simulate on an archived twin'
        case 422: return `Simulation unavailable: ${err.message}`
        case 503: return 'ML service is temporarily unavailable — please try again shortly'
        default:  return err.message
      }
    }
    return err.message ?? 'An unexpected error occurred'
  }

  return (
    <div className="mt-6" data-testid="what-if-simulation-panel">
      {/* Section header */}
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <h2 className="text-sm font-semibold text-slate-200">What-If Simulation</h2>
          <SimulatedBadge />
        </div>
        {lastResult && (
          <button
            type="button"
            onClick={handleReset}
            className="rounded-md border border-surface-border bg-surface-raised px-3 py-1.5 text-xs text-slate-400 transition-colors hover:text-slate-100 focus:outline-none focus:ring-2 focus:ring-accent"
            aria-label="Reset simulation form and result"
          >
            ↺ New Simulation
          </button>
        )}
      </div>

      {/* Description */}
      <p className="mb-4 text-[11px] text-slate-500">
        Apply a hypothetical scenario to this patient's current Digital Twin state and
        predict the glucose spike probability for that scenario. The real Digital Twin
        is never modified. Results are labelled{' '}
        <span className="font-medium text-violet-400">SIMULATED</span>.
      </p>

      {/* Scenario input form */}
      {!lastResult && (
        <form
          onSubmit={(e) => void handleSubmit(e)}
          noValidate
          aria-label="What-if simulation scenario form"
          data-testid="simulation-form"
        >
          <div className="card p-5">
            <div className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">

              {/* Meal carbohydrates */}
              <div className="flex flex-col gap-1.5">
                <label
                  htmlFor="sim-meal-carbs"
                  className="text-[11px] font-medium text-slate-400"
                >
                  Meal carbohydrates (g)
                </label>
                <input
                  id="sim-meal-carbs"
                  type="number"
                  min={0}
                  max={500}
                  step={1}
                  value={form.mealCarbsGrams}
                  onChange={handleCarbsChange}
                  placeholder="e.g. 90"
                  disabled={simulation.isPending}
                  aria-describedby={formErrors.mealCarbsGrams ? 'sim-carbs-error' : undefined}
                  aria-invalid={Boolean(formErrors.mealCarbsGrams)}
                  className={`rounded-md border bg-surface px-3 py-2 text-xs text-slate-100 placeholder-slate-600
                    focus:outline-none focus:ring-2 focus:ring-accent disabled:opacity-50
                    ${formErrors.mealCarbsGrams
                      ? 'border-risk-high focus:ring-risk-high'
                      : 'border-surface-border'
                    }`}
                />
                <p className="text-[10px] text-slate-600">0–500 g. Leave blank to exclude.</p>
                {formErrors.mealCarbsGrams && (
                  <p
                    id="sim-carbs-error"
                    className="text-[11px] text-risk-high"
                    role="alert"
                    data-testid="carbs-error"
                  >
                    {formErrors.mealCarbsGrams}
                  </p>
                )}
              </div>

              {/* Activity level */}
              <div className="flex flex-col gap-1.5">
                <label
                  htmlFor="sim-activity-level"
                  className="text-[11px] font-medium text-slate-400"
                >
                  Activity level
                </label>
                <select
                  id="sim-activity-level"
                  value={form.activityLevel}
                  onChange={handleActivityChange}
                  disabled={simulation.isPending}
                  className="rounded-md border border-surface-border bg-surface px-3 py-2 text-xs text-slate-100
                    focus:outline-none focus:ring-2 focus:ring-accent disabled:opacity-50"
                >
                  <option value="">— Not specified —</option>
                  {ACTIVITY_LEVEL_OPTIONS.map((opt) => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
                <p className="text-[10px] text-slate-600">Leave blank to exclude.</p>
              </div>

              {/* Medication taken */}
              <div className="flex flex-col gap-1.5">
                <span className="text-[11px] font-medium text-slate-400">
                  Medication taken
                </span>
                <label className="flex cursor-pointer items-center gap-3 rounded-md border border-surface-border bg-surface px-3 py-2">
                  <input
                    type="checkbox"
                    id="sim-medication-taken"
                    checked={form.medicationTaken}
                    onChange={handleMedicationChange}
                    disabled={simulation.isPending}
                    className="h-4 w-4 rounded border-surface-border bg-surface accent-accent focus:outline-none focus:ring-2 focus:ring-accent disabled:opacity-50"
                    aria-label="Medication taken in this scenario"
                  />
                  <span className="text-xs text-slate-300">
                    {form.medicationTaken ? 'Yes — medication taken' : 'No — not taken'}
                  </span>
                </label>
                <p className="text-[10px] text-slate-600">
                  Whether medication was taken in this scenario.
                </p>
              </div>
            </div>

            {/* At-least-one error */}
            {formErrors.atLeastOne && (
              <p
                className="mt-3 text-[11px] text-risk-high"
                role="alert"
                data-testid="at-least-one-error"
              >
                {formErrors.atLeastOne}
              </p>
            )}

            {/* Submit */}
            <div className="mt-5 flex flex-wrap items-center gap-3">
              <button
                type="submit"
                disabled={simulation.isPending}
                data-testid="run-simulation-button"
                className="flex items-center gap-2 rounded-md bg-violet-600 px-5 py-2 text-xs font-semibold text-white
                  transition-colors hover:bg-violet-700 disabled:opacity-60
                  focus:outline-none focus:ring-2 focus:ring-violet-400"
                aria-label="Run what-if simulation with the provided scenario inputs"
              >
                {simulation.isPending && (
                  <span
                    className="inline-block h-3 w-3 animate-spin rounded-full border-2 border-white border-t-transparent"
                    aria-hidden="true"
                    data-testid="loading-spinner"
                  />
                )}
                {simulation.isPending ? 'Running Simulation…' : '🔬 Run Simulation'}
              </button>

              {simulation.isPending && (
                <p className="text-[11px] text-slate-400" aria-live="polite">
                  Querying the XGBoost model pipeline…
                </p>
              )}
            </div>

            {/* API error */}
            {simulation.isError && (
              <div
                className="mt-3 rounded border border-risk-high/30 bg-risk-high/5 p-3"
                role="alert"
                data-testid="simulation-error"
              >
                <p className="text-xs font-semibold text-risk-high">Simulation failed</p>
                <p className="mt-1 text-[11px] text-slate-400">{getApiErrorMessage()}</p>
              </div>
            )}

            {/* Static disclaimer on the form */}
            <p className="mt-4 rounded border border-violet-500/20 bg-violet-950/20 px-3 py-2 text-[10px] text-slate-400">
              <strong className="text-violet-300">⚠ Hypothetical scenario only.</strong>{' '}
              This simulation does not modify the patient's Digital Twin or medical record.
              Results are for clinical decision support — not a diagnosis or treatment recommendation.
            </p>
          </div>
        </form>
      )}

      {/* Simulation result */}
      {lastResult && <SimulationResultCard result={lastResult} />}
    </div>
  )
}
