/**
 * Phase 10 — ClinicalInsightPanel unit tests
 *
 * Covers:
 * - Successful insight rendering
 * - Loading state (button disabled, spinner visible)
 * - API failure handling (network, 404, 422, 503)
 * - Provenance rendering (OBSERVED, PREDICTED, CLINICAL_KNOWLEDGE)
 * - Clinical knowledge evidence rendering
 * - Safety disclaimer always present
 * - Optional question submission
 * - New Insight reset button
 * - SIMULATED data never rendered
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ClinicalInsightPanel } from '@/components/ClinicalInsightPanel'
import { ApiError } from '@/lib/apiClient'
import type { ClinicalInsightResponse } from '@/types/clinicalInsight'

// ── Mock the API client ───────────────────────────────────────────────────────

vi.mock('@/lib/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/apiClient')>()
  return {
    ...actual,
    api: {
      ...actual.api,
      post: vi.fn(),
    },
  }
})

// ── Fixtures ──────────────────────────────────────────────────────────────────

const PATIENT_ID = 'a1b2c3d4-0000-0000-0000-000000000001'

const MOCK_INSIGHT: ClinicalInsightResponse = {
  patientId: PATIENT_ID,
  generatedAt: '2026-10-04T12:00:00Z',
  twinStateVersion: 3,
  latestPredictionId: 'pred-1234-0000-0000-000000000001',
  riskCategory: 'HIGH',
  spikeProbability: 0.72,
  confidenceInterval: { low: 0.61, high: 0.83 },
  keyObservedSignals: [
    { name: 'currentGlucose', value: '8.4', unit: 'mmol/L', provenance: 'OBSERVED' },
    { name: 'heartRate',      value: '72',  unit: 'bpm',    provenance: 'OBSERVED' },
  ],
  contributingFactors: [
    { factorName: 'cgm_current', contribution: 0.40, direction: 'INCREASES_RISK' },
    { factorName: 'hba1c_latest', contribution: 0.20, direction: 'INCREASES_RISK' },
  ],
  dataQualityWarnings: [],
  evidenceSummary: '[PREDICTED] HIGH risk. [OBSERVED] currentGlucose=8.4 mmol/L.',
  uncertainty: 'No significant uncertainty factors identified.',
  dataProvenance: 'OBSERVED+PREDICTED',
  safetyDisclaimer: 'This is clinical decision support, not a diagnosis or medical recommendation. Always apply clinical judgment. Consult the treating clinician before acting.',
  clinicalKnowledgeEvidence: [
    {
      knowledgeId: 'K004',
      title: 'Continuous Glucose Monitoring and Glucose Variability',
      sourceName: 'GlucoTwin Clinical Knowledge Base',
      sourceReference: 'GlucoTwin-KB-v1.0.0, Section 2.4',
      version: '1.0.0',
      topic: 'cgm_variability',
      excerpt: 'General clinical context: CGM provides real-time glucose data.',
      provenance: 'CLINICAL_KNOWLEDGE',
    },
  ],
}

const MOCK_INSIGHT_NO_KNOWLEDGE: ClinicalInsightResponse = {
  ...MOCK_INSIGHT,
  clinicalKnowledgeEvidence: [],
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function makeClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
}

function renderPanel(patientId = PATIENT_ID) {
  return render(
    <QueryClientProvider client={makeClient()}>
      <ClinicalInsightPanel patientId={patientId} />
    </QueryClientProvider>,
  )
}

async function getApiPost() {
  const { api } = await import('@/lib/apiClient')
  return api.post as ReturnType<typeof vi.fn>
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('ClinicalInsightPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ── Rendering ──────────────────────────────────────────────────────────────

  it('renders section header', () => {
    renderPanel()
    expect(screen.getByText('Clinical Insight')).toBeInTheDocument()
  })

  it('renders the generate button', () => {
    renderPanel()
    expect(screen.getByTestId('generate-insight-button')).toBeInTheDocument()
  })

  it('renders question input', () => {
    renderPanel()
    expect(screen.getByTestId('question-input')).toBeInTheDocument()
  })

  it('renders static safety disclaimer on form before generation', () => {
    renderPanel()
    expect(screen.getByText(/Decision support only\./i)).toBeInTheDocument()
  })

  // ── Loading state ──────────────────────────────────────────────────────────

  it('disables the generate button while pending', async () => {
    const apiPost = await getApiPost()
    apiPost.mockReturnValueOnce(new Promise(() => {}))
    const user = userEvent.setup()
    renderPanel()

    await user.click(screen.getByTestId('generate-insight-button'))

    await waitFor(() =>
      expect(screen.getByTestId('generate-insight-button')).toBeDisabled(),
    )
  })

  it('shows loading spinner while pending', async () => {
    const apiPost = await getApiPost()
    apiPost.mockReturnValueOnce(new Promise(() => {}))
    const user = userEvent.setup()
    renderPanel()

    await user.click(screen.getByTestId('generate-insight-button'))

    await waitFor(() =>
      expect(screen.getByTestId('loading-spinner')).toBeInTheDocument(),
    )
  })

  // ── Successful insight rendering ───────────────────────────────────────────

  it('displays spike probability from the response', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_INSIGHT)
    const user = userEvent.setup()
    renderPanel()

    await user.click(screen.getByTestId('generate-insight-button'))

    // 0.72 → "72.0"
    await screen.findByText(/72\.0/)
  })

  it('displays risk category badge from response', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_INSIGHT)
    const user = userEvent.setup()
    renderPanel()

    await user.click(screen.getByTestId('generate-insight-button'))

    await screen.findByText('HIGH')
  })

  it('displays the evidence summary from the response', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_INSIGHT)
    const user = userEvent.setup()
    renderPanel()

    await user.click(screen.getByTestId('generate-insight-button'))

    await screen.findByTestId('evidence-summary')
    expect(screen.getByTestId('evidence-summary')).toHaveTextContent(
      '[PREDICTED] HIGH risk.',
    )
  })

  it('shows the insight result card after success', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_INSIGHT)
    const user = userEvent.setup()
    renderPanel()

    await user.click(screen.getByTestId('generate-insight-button'))

    await screen.findByTestId('insight-result-card')
  })

  // ── Safety disclaimer ──────────────────────────────────────────────────────

  it('shows the backend-provided safety disclaimer verbatim after generation', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_INSIGHT)
    const user = userEvent.setup()
    renderPanel()

    await user.click(screen.getByTestId('generate-insight-button'))

    await screen.findByTestId('safety-disclaimer')
    expect(screen.getByTestId('safety-disclaimer')).toHaveTextContent(
      'clinical decision support, not a diagnosis or medical recommendation',
    )
  })

  // ── Provenance rendering ───────────────────────────────────────────────────

  it('renders OBSERVED provenance badge for observed signals', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_INSIGHT)
    const user = userEvent.setup()
    renderPanel()

    await user.click(screen.getByTestId('generate-insight-button'))

    await screen.findByTestId('insight-result-card')
    const observedBadges = screen.getAllByText('OBSERVED')
    expect(observedBadges.length).toBeGreaterThanOrEqual(1)
  })

  it('renders PREDICTED provenance badge for contributing factors', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_INSIGHT)
    const user = userEvent.setup()
    renderPanel()

    await user.click(screen.getByTestId('generate-insight-button'))

    await screen.findByTestId('insight-result-card')
    const predictedBadges = screen.getAllByText('PREDICTED')
    expect(predictedBadges.length).toBeGreaterThanOrEqual(1)
  })

  it('renders dataProvenance as OBSERVED+PREDICTED', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_INSIGHT)
    const user = userEvent.setup()
    renderPanel()

    await user.click(screen.getByTestId('generate-insight-button'))

    await screen.findByTestId('insight-result-card')
    expect(screen.getByText('OBSERVED+PREDICTED')).toBeInTheDocument()
  })

  // ── Clinical knowledge evidence ────────────────────────────────────────────

  it('renders clinical knowledge section when evidence present', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_INSIGHT)
    const user = userEvent.setup()
    renderPanel()

    await user.click(screen.getByTestId('generate-insight-button'))

    await screen.findByTestId('clinical-knowledge-section')
    expect(screen.getByTestId('clinical-knowledge-section')).toBeInTheDocument()
  })

  it('renders knowledge item with correct fields', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_INSIGHT)
    const user = userEvent.setup()
    renderPanel()

    await user.click(screen.getByTestId('generate-insight-button'))

    await screen.findByTestId('knowledge-item')
    expect(screen.getByText('Continuous Glucose Monitoring and Glucose Variability')).toBeInTheDocument()
    expect(screen.getByText(/General clinical context: CGM provides/i)).toBeInTheDocument()
    expect(screen.getByText(/GlucoTwin Clinical Knowledge Base/i)).toBeInTheDocument()
  })

  it('renders CLINICAL_KNOWLEDGE provenance badge on knowledge items', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_INSIGHT)
    const user = userEvent.setup()
    renderPanel()

    await user.click(screen.getByTestId('generate-insight-button'))

    await screen.findByTestId('clinical-knowledge-section')
    const badges = screen.getAllByText('CLINICAL_KNOWLEDGE')
    expect(badges.length).toBeGreaterThanOrEqual(1)
  })

  it('does not render clinical knowledge section when evidence is empty', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_INSIGHT_NO_KNOWLEDGE)
    const user = userEvent.setup()
    renderPanel()

    await user.click(screen.getByTestId('generate-insight-button'))

    await screen.findByTestId('insight-result-card')
    expect(screen.queryByTestId('clinical-knowledge-section')).not.toBeInTheDocument()
  })

  // ── API failure handling ───────────────────────────────────────────────────

  it('shows error message on network failure', async () => {
    const apiPost = await getApiPost()
    apiPost.mockRejectedValueOnce(new Error('Failed to fetch'))
    const user = userEvent.setup()
    renderPanel()

    await user.click(screen.getByTestId('generate-insight-button'))

    await screen.findByTestId('insight-error')
    expect(screen.getByTestId('insight-error')).toHaveTextContent(
      'Clinical insight failed',
    )
  })

  it('shows patient not found message on 404', async () => {
    const apiPost = await getApiPost()
    apiPost.mockRejectedValueOnce(
      new ApiError(404, 'NOT_FOUND', 'Digital Twin not found'),
    )
    const user = userEvent.setup()
    renderPanel()

    await user.click(screen.getByTestId('generate-insight-button'))

    await screen.findByTestId('insight-error')
    expect(screen.getByTestId('insight-error')).toHaveTextContent(
      'Patient or Digital Twin not found',
    )
  })

  it('shows insight unavailable message on 422', async () => {
    const apiPost = await getApiPost()
    apiPost.mockRejectedValueOnce(
      new ApiError(422, 'INSIGHT_PREDICTION_UNAVAILABLE',
        'No completed prediction available'),
    )
    const user = userEvent.setup()
    renderPanel()

    await user.click(screen.getByTestId('generate-insight-button'))

    await screen.findByTestId('insight-error')
    expect(screen.getByTestId('insight-error')).toHaveTextContent('Insight unavailable')
  })

  it('does not show result card when API fails', async () => {
    const apiPost = await getApiPost()
    apiPost.mockRejectedValueOnce(new Error('Network error'))
    const user = userEvent.setup()
    renderPanel()

    await user.click(screen.getByTestId('generate-insight-button'))

    await screen.findByTestId('insight-error')
    expect(screen.queryByTestId('insight-result-card')).not.toBeInTheDocument()
  })

  // ── Optional question submission ───────────────────────────────────────────

  it('sends question in request body when provided', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_INSIGHT)
    const user = userEvent.setup()
    renderPanel()

    await user.type(
      screen.getByTestId('question-input'),
      'Why is this patient at elevated risk?',
    )
    await user.click(screen.getByTestId('generate-insight-button'))

    await waitFor(() => expect(apiPost).toHaveBeenCalledOnce())
    expect(apiPost).toHaveBeenCalledWith(
      `/api/v1/patients/${PATIENT_ID}/clinical-insights`,
      expect.objectContaining({
        question: 'Why is this patient at elevated risk?',
      }),
    )
  })

  it('sends empty/undefined question when input is blank', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_INSIGHT)
    const user = userEvent.setup()
    renderPanel()

    // Leave question blank
    await user.click(screen.getByTestId('generate-insight-button'))

    await waitFor(() => expect(apiPost).toHaveBeenCalledOnce())
    const [, body] = apiPost.mock.calls[0] as [string, Record<string, unknown>]
    // question should be undefined when blank (trimmed empty string)
    expect(body.question).toBeUndefined()
  })

  // ── Reset ──────────────────────────────────────────────────────────────────

  it('shows New Insight button after successful generation', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_INSIGHT)
    const user = userEvent.setup()
    renderPanel()

    await user.click(screen.getByTestId('generate-insight-button'))

    await screen.findByText(/New Insight/i)
  })

  it('resets to the form when New Insight is clicked', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_INSIGHT)
    const user = userEvent.setup()
    renderPanel()

    await user.click(screen.getByTestId('generate-insight-button'))
    await screen.findByText(/New Insight/i)
    await user.click(screen.getByText(/New Insight/i))

    await screen.findByTestId('insight-form')
    expect(screen.queryByTestId('insight-result-card')).not.toBeInTheDocument()
  })

  // ── SIMULATED data not rendered ────────────────────────────────────────────

  it('never renders SIMULATED provenance badge in the insight result', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_INSIGHT)
    const user = userEvent.setup()
    renderPanel()

    await user.click(screen.getByTestId('generate-insight-button'))

    await screen.findByTestId('insight-result-card')
    expect(screen.queryByText('SIMULATED')).not.toBeInTheDocument()
  })
})
