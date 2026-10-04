/**
 * Phase 10 — Doctor Clinical Insights Panel
 *
 * Renders the clinical insight generation form and displays the structured
 * result from the Phase 8/9 agent orchestration backend.
 *
 * Provenance is displayed exactly as returned by the backend:
 *   OBSERVED          → patient wearable/EHR data
 *   PREDICTED         → XGBoost model output
 *   CLINICAL_KNOWLEDGE → general educational context (not patient-specific)
 *
 * No clinical values are fabricated. The safety disclaimer is always shown
 * verbatim from the backend response.
 * SIMULATED data is never rendered here.
 */

import { useState } from 'react'
import { useGenerateClinicalInsight } from '@/hooks/useClinicalInsight'
import { ApiError } from '@/lib/apiClient'
import type { RiskCategory } from '@/types/prediction'
import type {
  AgentExecutionTrace,
  AgentStepTrace,
  ClinicalInsightResponse,
  InsightContributingFactor,
  ClinicalKnowledgeEvidence,
  ObservedSignal,
} from '@/types/clinicalInsight'

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

// ── Provenance badges ─────────────────────────────────────────────────────────

function ObservedBadge() {
  return (
    <span className="rounded border border-slate-600 px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide text-slate-400">
      OBSERVED
    </span>
  )
}

function PredictedBadge() {
  return (
    <span className="rounded border border-accent/40 px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide text-accent">
      PREDICTED
    </span>
  )
}

function KnowledgeBadge() {
  return (
    <span className="rounded border border-emerald-500/40 px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide text-emerald-400">
      CLINICAL_KNOWLEDGE
    </span>
  )
}

// ── Contributing factors ──────────────────────────────────────────────────────

function ContributingFactorsSection({ factors }: { factors: InsightContributingFactor[] }) {
  if (factors.length === 0) return null
  const maxContrib = Math.max(...factors.map((f) => f.contribution), 0.01)
  return (
    <div className="mt-4 border-t border-surface-border pt-4">
      <div className="mb-2 flex items-center gap-2">
        <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">
          Contributing Factors
        </p>
        <PredictedBadge />
      </div>
      <p className="mb-3 text-[10px] text-slate-600">
        Statistical model drivers (SHAP values) — not causal clinical pathways.
      </p>
      <ul className="flex flex-col gap-2" role="list">
        {factors.map((f) => (
          <li key={f.factorName} className="flex flex-col gap-1">
            <div className="flex items-center justify-between">
              <span className="font-mono text-[11px] text-slate-300">{f.factorName}</span>
              <span className={`text-[11px] font-medium ${
                f.direction === 'INCREASES_RISK' ? 'text-risk-high' : 'text-risk-low'
              }`}>
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

// ── Observed signals ──────────────────────────────────────────────────────────

function ObservedSignalsSection({ signals }: { signals: ObservedSignal[] }) {
  if (signals.length === 0) return null
  return (
    <div className="mt-4 border-t border-surface-border pt-4">
      <div className="mb-2 flex items-center gap-2">
        <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">
          Key Observed Signals
        </p>
        <ObservedBadge />
      </div>
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
        {signals.map((s) => (
          <div
            key={s.name}
            className="rounded border border-surface-border bg-surface px-3 py-2"
          >
            <p className="text-[10px] text-slate-500">{s.name}</p>
            <p className="mt-0.5 font-mono text-xs font-semibold text-slate-200">
              {s.value}{s.unit ? ` ${s.unit}` : ''}
            </p>
          </div>
        ))}
      </div>
    </div>
  )
}

// ── Clinical knowledge evidence (Phase 9) ─────────────────────────────────────

function ClinicalKnowledgeSection({ items }: { items: ClinicalKnowledgeEvidence[] }) {
  if (items.length === 0) return null
  return (
    <div className="mt-4 border-t border-surface-border pt-4" data-testid="clinical-knowledge-section">
      <div className="mb-2 flex items-center gap-2">
        <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">
          General Clinical Context
        </p>
        <KnowledgeBadge />
      </div>
      <p className="mb-3 text-[10px] text-slate-600">
        General educational context retrieved for the identified risk factors.
        This is not patient-specific evidence — apply clinical judgment.
      </p>
      <ul className="flex flex-col gap-3" role="list">
        {items.map((item) => (
          <li
            key={item.knowledgeId}
            className="rounded border border-emerald-500/20 bg-emerald-950/10 p-3"
            data-testid="knowledge-item"
          >
            <div className="mb-1.5 flex flex-wrap items-center gap-2">
              <span className="font-mono text-[10px] text-emerald-500/70">{item.knowledgeId}</span>
              <span className="text-[11px] font-semibold text-slate-300">{item.title}</span>
              <KnowledgeBadge />
            </div>
            <p className="mb-2 text-[11px] text-slate-400">{item.excerpt}</p>
            <div className="flex flex-wrap gap-x-4 gap-y-0.5 text-[10px] text-slate-600">
              <span>Source: {item.sourceName}</span>
              {item.sourceReference && <span>Ref: {item.sourceReference}</span>}
              <span>v{item.version}</span>
              <span>Topic: {item.topic}</span>
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}

// ── Phase 12: Pipeline execution trace ────────────────────────────────────────

function StepStatusBadge({ status }: { status: AgentStepTrace['status'] }) {
  if (status === 'SUCCESS') return (
    <span className="text-[10px] font-semibold text-risk-low">✓</span>
  )
  if (status === 'FAILURE') return (
    <span className="text-[10px] font-semibold text-risk-high">✗</span>
  )
  return (
    <span className="text-[10px] font-semibold text-slate-500">–</span>
  )
}

function PipelineExecutionSection({ trace }: { trace: AgentExecutionTrace }) {
  const [expanded, setExpanded] = useState(false)

  return (
    <div
      className="mt-4 border-t border-surface-border pt-4"
      data-testid="pipeline-execution-section"
    >
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        className="flex w-full items-center justify-between gap-2 text-left
          focus:outline-none focus:ring-1 focus:ring-accent rounded"
        aria-expanded={expanded}
        aria-controls="pipeline-execution-details"
      >
        <div className="flex items-center gap-2">
          <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-500">
            🧠 Pipeline Execution
          </p>
          <span
            className="font-mono text-[10px] text-slate-500"
            data-testid="pipeline-total-duration"
          >
            {trace.totalDurationMs} ms total
          </span>
          {trace.traceId && (
            <span
              className="font-mono text-[10px] text-slate-600"
              data-testid="pipeline-trace-id"
            >
              trace: {trace.traceId}
            </span>
          )}
        </div>
        <span className="text-[10px] text-slate-600" aria-hidden="true">
          {expanded ? '▲' : '▼'}
        </span>
      </button>

      {expanded && (
        <div
          id="pipeline-execution-details"
          className="mt-2 flex flex-col gap-1"
          data-testid="pipeline-steps"
        >
          {trace.steps.map((step) => (
            <div
              key={step.agentName}
              className="flex items-center gap-2 rounded px-2 py-1 text-[11px]
                hover:bg-surface-raised"
              data-testid={`pipeline-step-${step.agentName.replace(/\s+/g, '-').toLowerCase()}`}
            >
              <StepStatusBadge status={step.status} />
              <span
                className={`w-36 flex-shrink-0 font-medium ${
                  step.status === 'SKIPPED' ? 'text-slate-600' : 'text-slate-300'
                }`}
                data-testid="step-name"
              >
                {step.agentName}
              </span>
              <span
                className="w-16 flex-shrink-0 font-mono text-[10px] text-slate-500"
                data-testid="step-duration"
              >
                {step.durationMs} ms
              </span>
              <span className="truncate text-[10px] text-slate-600">
                {step.detail}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

// ── Data quality warnings ─────────────────────────────────────────────────────

function DataQualityWarnings({ warnings }: { warnings: string[] }) {
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

// ── Insight result card ───────────────────────────────────────────────────────

function InsightResultCard({ insight }: { insight: ClinicalInsightResponse }) {
  const ci = insight.confidenceInterval
  return (
    <div className="flex flex-col gap-4" data-testid="insight-result-card">

      {/* Main result card */}
      <SectionCard
        title="Clinical Insight Result"
        badge={<PredictedBadge />}
        className="col-span-full"
      >
        {/* Spike probability + risk hero */}
        <div className="mb-4 flex flex-wrap items-center gap-6">
          <div>
            <p className="text-[10px] text-slate-500">Spike probability</p>
            <p className="mt-0.5 text-4xl font-bold tabular-nums text-slate-100">
              {(insight.spikeProbability * 100).toFixed(1)}
              <span className="text-xl text-slate-400">%</span>
            </p>
          </div>
          <div>
            <p className="text-[10px] text-slate-500">Risk category</p>
            <div className="mt-1">
              <RiskBadge category={insight.riskCategory} />
            </div>
          </div>
          <div>
            <p className="text-[10px] text-slate-500">95% confidence interval</p>
            <p className="mt-0.5 font-mono text-xs text-slate-300">
              [{fmtPercent(ci.low)} – {fmtPercent(ci.high)}]
            </p>
          </div>
        </div>

        {/* Meta row */}
        <div className="mb-4 flex flex-wrap gap-x-5 gap-y-1 border-t border-surface-border pt-3 text-[11px] text-slate-500">
          <span>Twin v{insight.twinStateVersion}</span>
          <span>Generated: <time dateTime={insight.generatedAt}>{fmtDateTime(insight.generatedAt)}</time></span>
          <span className="font-mono">Prediction: {insight.latestPredictionId.slice(0, 8)}…</span>
          <span>Provenance: <span className="text-slate-400">{insight.dataProvenance}</span></span>
        </div>

        {/* Evidence summary */}
        <div className="mb-4 rounded border border-surface-border bg-surface p-3">
          <p className="mb-1 text-[10px] font-semibold uppercase tracking-wider text-slate-500">
            Evidence Summary
          </p>
          <p className="text-[11px] leading-relaxed text-slate-300"
             data-testid="evidence-summary">
            {insight.evidenceSummary}
          </p>
        </div>

        {/* Uncertainty */}
        {insight.uncertainty && (
          <div className="mb-4 rounded border border-risk-moderate/20 bg-risk-moderate/5 p-3">
            <p className="mb-1 text-[10px] font-semibold uppercase tracking-wider text-risk-moderate">
              Uncertainty
            </p>
            <p className="text-[11px] text-slate-400">{insight.uncertainty}</p>
          </div>
        )}

        {/* Data quality warnings */}
        {insight.dataQualityWarnings.length > 0 && (
          <div className="mb-4">
            <DataQualityWarnings warnings={insight.dataQualityWarnings} />
          </div>
        )}

        {/* Observed signals */}
        <ObservedSignalsSection signals={insight.keyObservedSignals} />

        {/* Contributing factors */}
        <ContributingFactorsSection factors={insight.contributingFactors} />

        {/* Clinical knowledge evidence (Phase 9) */}
        <ClinicalKnowledgeSection items={insight.clinicalKnowledgeEvidence} />

        {/* Safety disclaimer — always shown verbatim from backend */}
        <p
          className="mt-4 rounded border border-surface-border bg-surface px-3 py-2 text-[10px] text-slate-500"
          data-testid="safety-disclaimer"
        >
          <strong className="text-slate-400">Decision support only.</strong>{' '}
          {insight.safetyDisclaimer}
        </p>

        {/* Phase 12: Pipeline execution trace (collapsed by default) */}
        {insight.executionTrace && (
          <PipelineExecutionSection trace={insight.executionTrace} />
        )}
      </SectionCard>
    </div>
  )
}

// ── API error message helper ──────────────────────────────────────────────────

function getErrorMessage(err: Error): string {
  if (err instanceof ApiError) {
    switch (err.status) {
      case 404: return 'Patient or Digital Twin not found.'
      case 422: return `Insight unavailable: ${err.message}`
      case 503: return 'ML service temporarily unavailable — please try again shortly.'
      default:  return err.message
    }
  }
  return err.message ?? 'An unexpected error occurred.'
}

// ── Clinical Insight Panel (main export) ──────────────────────────────────────

interface ClinicalInsightPanelProps {
  patientId: string
}

export function ClinicalInsightPanel({ patientId }: ClinicalInsightPanelProps) {
  const [question, setQuestion] = useState('')
  const [lastResult, setLastResult] = useState<ClinicalInsightResponse | null>(null)

  const mutation = useGenerateClinicalInsight(patientId)

  async function handleGenerate() {
    try {
      const result = await mutation.mutateAsync({ question: question.trim() || undefined })
      setLastResult(result)
    } catch {
      // error displayed via mutation.isError
    }
  }

  function handleReset() {
    setLastResult(null)
    mutation.reset()
    setQuestion('')
  }

  return (
    <div className="mt-6" data-testid="clinical-insight-panel">
      {/* Section header */}
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <h2 className="text-sm font-semibold text-slate-200">Clinical Insight</h2>
          <span className="rounded border border-accent/40 px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide text-accent">
            AI ANALYSIS
          </span>
        </div>
        {lastResult && (
          <button
            type="button"
            onClick={handleReset}
            className="rounded-md border border-surface-border bg-surface-raised px-3 py-1.5 text-xs text-slate-400 transition-colors hover:text-slate-100 focus:outline-none focus:ring-2 focus:ring-accent"
            aria-label="Generate a new clinical insight"
          >
            ↺ New Insight
          </button>
        )}
      </div>

      <p className="mb-4 text-[11px] text-slate-500">
        Runs the AI agent pipeline (Twin Analysis → Prediction Analysis → Risk Evidence +
        Knowledge Retrieval) to produce a structured clinical decision-support insight.
        A completed prediction must exist for this patient.
      </p>

      {/* Input form — shown only before a result */}
      {!lastResult && (
        <div className="card p-5" data-testid="insight-form">
          {/* Optional clinician question */}
          <div className="mb-4 flex flex-col gap-1.5">
            <label
              htmlFor="insight-question"
              className="text-[11px] font-medium text-slate-400"
            >
              Clinician question{' '}
              <span className="text-slate-600">(optional, max 500 chars)</span>
            </label>
            <input
              id="insight-question"
              type="text"
              maxLength={500}
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              disabled={mutation.isPending}
              placeholder="e.g. Why is this patient currently at elevated glucose spike risk?"
              className="rounded-md border border-surface-border bg-surface px-3 py-2 text-xs
                text-slate-100 placeholder-slate-600
                focus:outline-none focus:ring-2 focus:ring-accent disabled:opacity-50"
              aria-label="Optional clinician question for the insight"
              data-testid="question-input"
            />
            <p className="text-[10px] text-slate-600">
              The question provides context and is recorded, but does not alter data retrieval.
            </p>
          </div>

          {/* Generate button */}
          <div className="flex flex-wrap items-center gap-3">
            <button
              type="button"
              onClick={() => void handleGenerate()}
              disabled={mutation.isPending}
              data-testid="generate-insight-button"
              className="flex items-center gap-2 rounded-md bg-accent px-5 py-2 text-xs
                font-semibold text-white transition-colors hover:bg-accent-hover
                disabled:opacity-60 focus:outline-none focus:ring-2 focus:ring-accent"
              aria-label="Generate clinical insight for this patient"
            >
              {mutation.isPending && (
                <span
                  className="inline-block h-3 w-3 animate-spin rounded-full border-2
                    border-white border-t-transparent"
                  aria-hidden="true"
                  data-testid="loading-spinner"
                />
              )}
              {mutation.isPending ? 'Generating…' : '🧠 Generate Clinical Insight'}
            </button>
            {mutation.isPending && (
              <p className="text-[11px] text-slate-400" aria-live="polite">
                Running agent pipeline…
              </p>
            )}
          </div>

          {/* API error */}
          {mutation.isError && (
            <div
              className="mt-3 rounded border border-risk-high/30 bg-risk-high/5 p-3"
              role="alert"
              data-testid="insight-error"
            >
              <p className="text-xs font-semibold text-risk-high">Clinical insight failed</p>
              <p className="mt-1 text-[11px] text-slate-400">
                {getErrorMessage(mutation.error)}
              </p>
            </div>
          )}

          {/* Static disclaimer on form */}
          <p className="mt-4 rounded border border-surface-border bg-surface px-3 py-2 text-[10px] text-slate-500">
            <strong className="text-slate-400">Decision support only.</strong>{' '}
            Clinical insights are for decision support, not diagnosis or treatment recommendation.
            Always apply clinical judgment.
          </p>
        </div>
      )}

      {/* Result */}
      {lastResult && <InsightResultCard insight={lastResult} />}
    </div>
  )
}
