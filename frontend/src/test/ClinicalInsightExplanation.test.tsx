/**
 * Phase 13 — ClinicalInsightPanel AI explanation section tests.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ClinicalInsightPanel } from '@/components/ClinicalInsightPanel'
import type { ClinicalInsightResponse } from '@/types/clinicalInsight'

vi.mock('@/lib/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/apiClient')>()
  return { ...actual, api: { ...actual.api, post: vi.fn() } }
})

const PATIENT_ID = 'a1b2c3d4-0000-0000-0000-000000000001'

const BASE: Omit<ClinicalInsightResponse, 'explanation' | 'executionTrace'> = {
  patientId: PATIENT_ID,
  generatedAt: '2026-10-04T12:00:00Z',
  twinStateVersion: 3,
  latestPredictionId: 'pred-0001',
  riskCategory: 'HIGH',
  spikeProbability: 0.72,
  confidenceInterval: { low: 0.61, high: 0.83 },
  keyObservedSignals: [{ name: 'currentGlucose', value: '8.4', unit: 'mmol/L', provenance: 'OBSERVED' }],
  contributingFactors: [{ factorName: 'cgm_current', contribution: 0.40, direction: 'INCREASES_RISK' }],
  dataQualityWarnings: [],
  evidenceSummary: '[PREDICTED] HIGH risk.',
  uncertainty: 'No uncertainty.',
  dataProvenance: 'OBSERVED+PREDICTED',
  safetyDisclaimer: 'This is clinical decision support, not a diagnosis or medical recommendation. Always apply clinical judgment. Consult the treating clinician before acting.',
  clinicalKnowledgeEvidence: [],
}

const WITH_EXPLANATION: ClinicalInsightResponse = {
  ...BASE,
  explanation: 'The patient shows elevated glucose spike risk based on high CGM readings and reduced HRV, according to the model.',
}

const WITHOUT_EXPLANATION: ClinicalInsightResponse = { ...BASE }
const NULL_EXPLANATION: ClinicalInsightResponse   = { ...BASE, explanation: null }

function makeClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
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

async function generateWith(insight: ClinicalInsightResponse) {
  const apiPost = await getApiPost()
  apiPost.mockResolvedValueOnce(insight)
  const user = userEvent.setup()
  renderPanel()
  await user.click(screen.getByTestId('generate-insight-button'))
  await screen.findByTestId('insight-result-card')
}

describe('ClinicalInsightPanel — AI Explanation (Phase 13)', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('renders AI explanation section when explanation is present', async () => {
    await generateWith(WITH_EXPLANATION)
    expect(screen.getByTestId('ai-explanation-section')).toBeInTheDocument()
  })

  it('renders explanation text from the response', async () => {
    await generateWith(WITH_EXPLANATION)
    expect(screen.getByTestId('ai-explanation-text')).toHaveTextContent(
      'elevated glucose spike risk'
    )
  })

  it('hides AI explanation section when explanation field is absent', async () => {
    await generateWith(WITHOUT_EXPLANATION)
    expect(screen.queryByTestId('ai-explanation-section')).not.toBeInTheDocument()
  })

  it('hides AI explanation section when explanation is null', async () => {
    await generateWith(NULL_EXPLANATION)
    expect(screen.queryByTestId('ai-explanation-section')).not.toBeInTheDocument()
  })

  it('renders LLM badge on the explanation section', async () => {
    await generateWith(WITH_EXPLANATION)
    expect(screen.getByText('LLM')).toBeInTheDocument()
  })

  it('renders disclaimer text in explanation section', async () => {
    await generateWith(WITH_EXPLANATION)
    expect(screen.getByTestId('ai-explanation-section')).toHaveTextContent(
      'Not a medical recommendation'
    )
  })

  it('still renders spike probability when explanation is present', async () => {
    await generateWith(WITH_EXPLANATION)
    expect(screen.getByText(/72\.0/)).toBeInTheDocument()
  })
})
