/**
 * Phase 12 — ClinicalInsightPanel pipeline execution trace tests.
 *
 * Covers:
 * - pipeline execution section renders when trace present
 * - stage names render
 * - status badges render
 * - duration renders
 * - total duration renders
 * - trace ID renders when present
 * - section hidden when executionTrace absent
 * - section hidden when executionTrace null
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ClinicalInsightPanel } from '@/components/ClinicalInsightPanel'
import type { ClinicalInsightResponse } from '@/types/clinicalInsight'

// ── Mock API client ───────────────────────────────────────────────────────────

vi.mock('@/lib/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/apiClient')>()
  return {
    ...actual,
    api: { ...actual.api, post: vi.fn() },
  }
})

// ── Fixtures ──────────────────────────────────────────────────────────────────

const PATIENT_ID = 'a1b2c3d4-0000-0000-0000-000000000001'

const BASE_INSIGHT: Omit<ClinicalInsightResponse, 'executionTrace'> = {
  patientId: PATIENT_ID,
  generatedAt: '2026-10-04T12:00:00Z',
  twinStateVersion: 3,
  latestPredictionId: 'pred-0000-0000-0000-000000000001',
  riskCategory: 'HIGH',
  spikeProbability: 0.72,
  confidenceInterval: { low: 0.61, high: 0.83 },
  keyObservedSignals: [
    { name: 'currentGlucose', value: '8.4', unit: 'mmol/L', provenance: 'OBSERVED' },
  ],
  contributingFactors: [
    { factorName: 'cgm_current', contribution: 0.40, direction: 'INCREASES_RISK' },
  ],
  dataQualityWarnings: [],
  evidenceSummary: '[PREDICTED] HIGH risk.',
  uncertainty: 'No uncertainty.',
  dataProvenance: 'OBSERVED+PREDICTED',
  safetyDisclaimer:
    'This is clinical decision support, not a diagnosis or medical recommendation. Always apply clinical judgment. Consult the treating clinician before acting.',
  clinicalKnowledgeEvidence: [],
}

const TRACE_STEPS = [
  { agentName: 'Twin Analysis',       status: 'SUCCESS' as const, durationMs: 12, detail: 'Observed twin state analyzed — 1 signal(s)' },
  { agentName: 'Prediction Analysis', status: 'SUCCESS' as const, durationMs: 31, detail: 'Latest prediction analyzed — risk=HIGH' },
  { agentName: 'Risk Evidence',       status: 'SUCCESS' as const, durationMs: 4,  detail: 'Evidence aggregation completed — retrieved 1 clinical knowledge item(s)' },
  { agentName: 'Verification',        status: 'SUCCESS' as const, durationMs: 0,  detail: 'Verification completed with 0 warning(s)' },
  { agentName: 'Insight Assembly',    status: 'SUCCESS' as const, durationMs: 1,  detail: 'Clinical insight response constructed' },
  { agentName: 'Audit',               status: 'SUCCESS' as const, durationMs: 8,  detail: 'Audit record persisted' },
]

const INSIGHT_WITH_TRACE: ClinicalInsightResponse = {
  ...BASE_INSIGHT,
  executionTrace: {
    traceId: 'test-trace-abc123',
    startedAt: '2026-10-04T12:00:00Z',
    totalDurationMs: 184,
    steps: TRACE_STEPS,
  },
}

const INSIGHT_NO_TRACE: ClinicalInsightResponse = {
  ...BASE_INSIGHT,
  executionTrace: null,
}

const INSIGHT_TRACE_ABSENT: ClinicalInsightResponse = {
  ...BASE_INSIGHT,
  // executionTrace field not present at all (optional)
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function makeClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
}

function renderPanel() {
  return render(
    <QueryClientProvider client={makeClient()}>
      <ClinicalInsightPanel patientId={PATIENT_ID} />
    </QueryClientProvider>,
  )
}

async function getApiPost() {
  const { api } = await import('@/lib/apiClient')
  return api.post as ReturnType<typeof vi.fn>
}

async function generateInsight(insight: ClinicalInsightResponse) {
  const apiPost = await getApiPost()
  apiPost.mockResolvedValueOnce(insight)
  const user = userEvent.setup()
  renderPanel()
  await user.click(screen.getByTestId('generate-insight-button'))
  await screen.findByTestId('insight-result-card')
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('ClinicalInsightPanel — Pipeline Execution Trace (Phase 12)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // Test 1: Pipeline execution section renders when trace is present
  it('renders pipeline execution section when trace is present', async () => {
    await generateInsight(INSIGHT_WITH_TRACE)
    expect(screen.getByTestId('pipeline-execution-section')).toBeInTheDocument()
  })

  // Test 2: Total duration renders
  it('renders total pipeline duration', async () => {
    await generateInsight(INSIGHT_WITH_TRACE)
    expect(screen.getByTestId('pipeline-total-duration')).toHaveTextContent('184 ms total')
  })

  // Test 3: Trace ID renders when present
  it('renders trace ID when present', async () => {
    await generateInsight(INSIGHT_WITH_TRACE)
    expect(screen.getByTestId('pipeline-trace-id')).toHaveTextContent('test-trace-abc123')
  })

  // Test 4: Stage names render after expanding
  it('renders stage names after expanding pipeline section', async () => {
    const user = userEvent.setup()
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(INSIGHT_WITH_TRACE)
    renderPanel()
    await user.click(screen.getByTestId('generate-insight-button'))
    await screen.findByTestId('pipeline-execution-section')

    // Expand the section
    await user.click(screen.getByTestId('pipeline-execution-section').querySelector('button')!)

    await screen.findByTestId('pipeline-steps')
    const stepNames = screen.getAllByTestId('step-name')
    const names = stepNames.map((el) => el.textContent)
    expect(names).toContain('Twin Analysis')
    expect(names).toContain('Prediction Analysis')
    expect(names).toContain('Risk Evidence')
    expect(names).toContain('Verification')
    expect(names).toContain('Insight Assembly')
    expect(names).toContain('Audit')
  })

  // Test 5: Durations render after expanding
  it('renders step durations after expanding', async () => {
    const user = userEvent.setup()
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(INSIGHT_WITH_TRACE)
    renderPanel()
    await user.click(screen.getByTestId('generate-insight-button'))
    await screen.findByTestId('pipeline-execution-section')

    await user.click(screen.getByTestId('pipeline-execution-section').querySelector('button')!)

    await screen.findByTestId('pipeline-steps')
    const durations = screen.getAllByTestId('step-duration')
    expect(durations.length).toBeGreaterThan(0)
    // All duration cells should render with "ms"
    durations.forEach((el) => expect(el.textContent).toMatch(/\d+ ms/))
  })

  // Test 6: Status badges render (SUCCESS checkmarks)
  it('renders SUCCESS status indicators for all passed steps', async () => {
    const user = userEvent.setup()
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(INSIGHT_WITH_TRACE)
    renderPanel()
    await user.click(screen.getByTestId('generate-insight-button'))
    await screen.findByTestId('pipeline-execution-section')

    await user.click(screen.getByTestId('pipeline-execution-section').querySelector('button')!)

    await screen.findByTestId('pipeline-steps')
    // All 6 steps are SUCCESS → 6 checkmarks
    const checks = screen.getAllByText('✓')
    expect(checks.length).toBe(6)
  })

  // Test 7: Section hidden when executionTrace is null
  it('hides pipeline section when executionTrace is null', async () => {
    await generateInsight(INSIGHT_NO_TRACE)
    expect(screen.queryByTestId('pipeline-execution-section')).not.toBeInTheDocument()
  })

  // Test 8: Section hidden when executionTrace field is absent
  it('hides pipeline section when executionTrace field is absent', async () => {
    await generateInsight(INSIGHT_TRACE_ABSENT)
    expect(screen.queryByTestId('pipeline-execution-section')).not.toBeInTheDocument()
  })

  // Test 9: Existing insight fields still render with trace present
  it('still renders spike probability when trace is present', async () => {
    await generateInsight(INSIGHT_WITH_TRACE)
    expect(screen.getByText(/72\.0/)).toBeInTheDocument()
    expect(screen.getByText('HIGH')).toBeInTheDocument()
  })

  // Test 10: Trace section is collapsed by default (steps not visible)
  it('pipeline steps are hidden by default (collapsed)', async () => {
    await generateInsight(INSIGHT_WITH_TRACE)
    expect(screen.queryByTestId('pipeline-steps')).not.toBeInTheDocument()
  })
})
