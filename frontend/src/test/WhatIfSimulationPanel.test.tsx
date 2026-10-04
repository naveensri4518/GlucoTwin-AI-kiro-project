/**
 * Phase 7B — WhatIfSimulationPanel unit tests
 *
 * Covers:
 * - Valid simulation submission
 * - Invalid input (carbs out of range, at-least-one rule)
 * - Loading state (button disabled, spinner visible)
 * - Successful SIMULATED response rendering
 * - deltaVsBaseline null handling ("Baseline prediction unavailable")
 * - API failure handling (network error, 404, 503)
 * - Safety disclaimer rendering
 * - SIMULATED provenance badge rendering
 *
 * The backend XGBoost pipeline is mocked — no real HTTP calls are made.
 * No spike probabilities are fabricated; test fixtures mirror what the backend
 * would return.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { WhatIfSimulationPanel } from '@/components/WhatIfSimulationPanel'
import { ApiError } from '@/lib/apiClient'
import type { SimulationResponse } from '@/types/simulation'

// ── Mock the API client ───────────────────────────────────────────────────────
// We mock api.post so no real HTTP calls are made. The hook useRunSimulation
// calls api.post internally via useMutation.

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

// ── Test fixtures ─────────────────────────────────────────────────────────────

const PATIENT_ID = 'a1b2c3d4-0000-0000-0000-000000000001'

/** A complete, realistic simulation response. Matches SimulationResponse exactly. */
const MOCK_SIMULATION_RESPONSE: SimulationResponse = {
  simulationId: 'sim-0001-0000-0000-000000000001',
  patientId: PATIENT_ID,
  simulatedAt: '2026-10-04T10:00:00Z',
  predictionHorizonHours: 2,
  spikeProbability: 0.82,
  riskCategory: 'HIGH',
  confidenceInterval: { low: 0.74, high: 0.90 },
  topContributingFactors: [
    { factorName: 'meal_carbs_grams', contribution: 0.45, direction: 'INCREASES_RISK' },
    { factorName: 'activity_level',   contribution: 0.20, direction: 'DECREASES_RISK' },
  ],
  dataProvenance: 'SIMULATED',
  twinStateVersion: 3,
  modelVersion: 'xgb-v2.1.0',
  scenarioInputs: { mealCarbsGrams: 90, activityLevel: 'VIGOROUS', medicationTaken: false },
  deltaVsBaseline: 0.15,
  dataQualityWarnings: [],
  disclaimer: 'This is a hypothetical what-if scenario. Not a medical recommendation.',
}

/** Variant with deltaVsBaseline absent (null) */
const MOCK_RESPONSE_NO_BASELINE: SimulationResponse = {
  ...MOCK_SIMULATION_RESPONSE,
  deltaVsBaseline: null,
}

/** Variant with data quality warnings */
const MOCK_RESPONSE_WITH_WARNINGS: SimulationResponse = {
  ...MOCK_SIMULATION_RESPONSE,
  dataQualityWarnings: ['CGM reading is 45 minutes old', 'HRV data missing'],
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
}

function renderPanel(patientId = PATIENT_ID) {
  const client = makeQueryClient()
  return render(
    <QueryClientProvider client={client}>
      <WhatIfSimulationPanel patientId={patientId} />
    </QueryClientProvider>,
  )
}

async function getApiPost() {
  const { api } = await import('@/lib/apiClient')
  return api.post as ReturnType<typeof vi.fn>
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('WhatIfSimulationPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ── Rendering ──────────────────────────────────────────────────────────────

  it('renders the section header', () => {
    renderPanel()
    expect(screen.getByText('What-If Simulation')).toBeInTheDocument()
  })

  it('renders the SIMULATED provenance badge', () => {
    renderPanel()
    // The badge text appears in both the header and the form disclaimer
    const badges = screen.getAllByText('SIMULATED')
    expect(badges.length).toBeGreaterThanOrEqual(1)
  })

  it('renders the scenario input form', () => {
    renderPanel()
    expect(screen.getByTestId('simulation-form')).toBeInTheDocument()
    expect(screen.getByLabelText(/meal carbohydrates/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/activity level/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/medication taken/i)).toBeInTheDocument()
  })

  it('renders the Run Simulation button', () => {
    renderPanel()
    expect(screen.getByTestId('run-simulation-button')).toBeInTheDocument()
  })

  it('renders the static safety disclaimer on the form', () => {
    renderPanel()
    expect(screen.getByText(/hypothetical scenario only/i)).toBeInTheDocument()
  })

  // ── Input validation ───────────────────────────────────────────────────────

  it('shows at-least-one error when submitting empty form', async () => {
    const user = userEvent.setup()
    renderPanel()
    await user.click(screen.getByTestId('run-simulation-button'))
    expect(await screen.findByTestId('at-least-one-error')).toBeInTheDocument()
    expect(screen.getByTestId('at-least-one-error')).toHaveTextContent(
      /provide at least one scenario parameter/i,
    )
  })

  it('shows carbs validation error for negative value', async () => {
    const user = userEvent.setup()
    renderPanel()
    await user.type(screen.getByLabelText(/meal carbohydrates/i), '-5')
    await user.click(screen.getByTestId('run-simulation-button'))
    expect(await screen.findByTestId('carbs-error')).toHaveTextContent(/must be 0 or greater/i)
  })

  it('shows carbs validation error for value over 500', async () => {
    const user = userEvent.setup()
    renderPanel()
    await user.type(screen.getByLabelText(/meal carbohydrates/i), '501')
    await user.click(screen.getByTestId('run-simulation-button'))
    expect(await screen.findByTestId('carbs-error')).toHaveTextContent(/500/i)
  })

  it('does not call api.post when validation fails', async () => {
    const user = userEvent.setup()
    renderPanel()
    // Submit with no inputs
    await user.click(screen.getByTestId('run-simulation-button'))
    const apiPost = await getApiPost()
    expect(apiPost).not.toHaveBeenCalled()
  })

  it('clears at-least-one error when medication checkbox is ticked', async () => {
    const user = userEvent.setup()
    renderPanel()
    // Trigger the error
    await user.click(screen.getByTestId('run-simulation-button'))
    expect(await screen.findByTestId('at-least-one-error')).toBeInTheDocument()
    // Tick medication — should clear the error
    await user.click(screen.getByLabelText(/medication taken/i))
    await waitFor(() =>
      expect(screen.queryByTestId('at-least-one-error')).not.toBeInTheDocument(),
    )
  })

  // ── Valid submission ───────────────────────────────────────────────────────

  it('calls api.post with correct payload on valid submission', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_SIMULATION_RESPONSE)
    const user = userEvent.setup()
    renderPanel()

    await user.type(screen.getByLabelText(/meal carbohydrates/i), '90')
    await user.selectOptions(screen.getByLabelText(/activity level/i), 'VIGOROUS')
    await user.click(screen.getByTestId('run-simulation-button'))

    await waitFor(() => expect(apiPost).toHaveBeenCalledOnce())
    expect(apiPost).toHaveBeenCalledWith(
      `/api/v1/patients/${PATIENT_ID}/simulations`,
      expect.objectContaining({ mealCarbsGrams: 90, activityLevel: 'VIGOROUS' }),
    )
  })

  it('only includes non-empty fields in the request payload', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_SIMULATION_RESPONSE)
    const user = userEvent.setup()
    renderPanel()

    // Only set activity level — leave carbs blank and medication unchecked
    await user.selectOptions(screen.getByLabelText(/activity level/i), 'LIGHT')
    await user.click(screen.getByTestId('run-simulation-button'))

    await waitFor(() => expect(apiPost).toHaveBeenCalledOnce())
    const [, body] = apiPost.mock.calls[0] as [string, Record<string, unknown>]
    expect(body).toEqual({ activityLevel: 'LIGHT' })
    expect(body).not.toHaveProperty('mealCarbsGrams')
    expect(body).not.toHaveProperty('medicationTaken')
  })

  // ── Loading state ──────────────────────────────────────────────────────────

  it('disables the Run Simulation button while pending', async () => {
    const apiPost = await getApiPost()
    // Never resolves — keeps mutation in pending state
    apiPost.mockReturnValueOnce(new Promise(() => {}))
    const user = userEvent.setup()
    renderPanel()

    await user.type(screen.getByLabelText(/meal carbohydrates/i), '60')
    await user.click(screen.getByTestId('run-simulation-button'))

    await waitFor(() =>
      expect(screen.getByTestId('run-simulation-button')).toBeDisabled(),
    )
  })

  it('shows the loading spinner while pending', async () => {
    const apiPost = await getApiPost()
    apiPost.mockReturnValueOnce(new Promise(() => {}))
    const user = userEvent.setup()
    renderPanel()

    await user.type(screen.getByLabelText(/meal carbohydrates/i), '60')
    await user.click(screen.getByTestId('run-simulation-button'))

    await waitFor(() =>
      expect(screen.getByTestId('loading-spinner')).toBeInTheDocument(),
    )
  })

  // ── Successful SIMULATED response ──────────────────────────────────────────

  it('displays simulated spike probability from the response', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_SIMULATION_RESPONSE)
    const user = userEvent.setup()
    renderPanel()

    await user.type(screen.getByLabelText(/meal carbohydrates/i), '90')
    await user.click(screen.getByTestId('run-simulation-button'))

    // 0.82 → displayed as "82.0%"
    await screen.findByText(/82\.0/)
  })

  it('displays the risk category from the response', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_SIMULATION_RESPONSE)
    const user = userEvent.setup()
    renderPanel()

    await user.type(screen.getByLabelText(/meal carbohydrates/i), '90')
    await user.click(screen.getByTestId('run-simulation-button'))

    await screen.findByText('HIGH')
  })

  it('displays the SIMULATED badge on the result card', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_SIMULATION_RESPONSE)
    const user = userEvent.setup()
    renderPanel()

    await user.type(screen.getByLabelText(/meal carbohydrates/i), '90')
    await user.click(screen.getByTestId('run-simulation-button'))

    // After result appears, SIMULATED badge should be present
    await screen.findByText(/82\.0/)
    const badges = screen.getAllByText('SIMULATED')
    expect(badges.length).toBeGreaterThanOrEqual(1)
  })

  it('displays the backend-provided safety disclaimer verbatim', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_SIMULATION_RESPONSE)
    const user = userEvent.setup()
    renderPanel()

    await user.type(screen.getByLabelText(/meal carbohydrates/i), '90')
    await user.click(screen.getByTestId('run-simulation-button'))

    await screen.findByText(/This is a hypothetical what-if scenario\. Not a medical recommendation\./i)
  })

  it('displays contributing factors from the response', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_SIMULATION_RESPONSE)
    const user = userEvent.setup()
    renderPanel()

    await user.type(screen.getByLabelText(/meal carbohydrates/i), '90')
    await user.click(screen.getByTestId('run-simulation-button'))

    await screen.findByText('meal_carbs_grams')
    expect(screen.getByText('activity_level')).toBeInTheDocument()
  })

  it('displays delta vs baseline when present', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_SIMULATION_RESPONSE) // deltaVsBaseline: 0.15
    const user = userEvent.setup()
    renderPanel()

    await user.type(screen.getByLabelText(/meal carbohydrates/i), '90')
    await user.click(screen.getByTestId('run-simulation-button'))

    // 0.15 → "+15.0%"
    await screen.findByText(/\+15\.0%/)
  })

  // ── deltaVsBaseline null handling ──────────────────────────────────────────

  it('shows "Baseline prediction unavailable" when deltaVsBaseline is null', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_RESPONSE_NO_BASELINE)
    const user = userEvent.setup()
    renderPanel()

    await user.type(screen.getByLabelText(/meal carbohydrates/i), '90')
    await user.click(screen.getByTestId('run-simulation-button'))

    await screen.findByText(/Baseline prediction unavailable/i)
  })

  it('does NOT display a 0% delta when deltaVsBaseline is null', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_RESPONSE_NO_BASELINE)
    const user = userEvent.setup()
    renderPanel()

    await user.type(screen.getByLabelText(/meal carbohydrates/i), '90')
    await user.click(screen.getByTestId('run-simulation-button'))

    await screen.findByText(/Baseline prediction unavailable/i)
    // Should not show "+0.0%" or "0.0%" as the delta value
    expect(screen.queryByText(/^\+?0\.0%$/)).not.toBeInTheDocument()
  })

  // ── Data quality warnings ──────────────────────────────────────────────────

  it('displays data quality warnings when present', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_RESPONSE_WITH_WARNINGS)
    const user = userEvent.setup()
    renderPanel()

    await user.type(screen.getByLabelText(/meal carbohydrates/i), '90')
    await user.click(screen.getByTestId('run-simulation-button'))

    await screen.findByText(/CGM reading is 45 minutes old/i)
    expect(screen.getByText(/HRV data missing/i)).toBeInTheDocument()
  })

  // ── API error handling ─────────────────────────────────────────────────────

  it('shows an error message on network failure', async () => {
    const apiPost = await getApiPost()
    apiPost.mockRejectedValueOnce(new Error('Failed to fetch'))
    const user = userEvent.setup()
    renderPanel()

    await user.type(screen.getByLabelText(/meal carbohydrates/i), '90')
    await user.click(screen.getByTestId('run-simulation-button'))

    await screen.findByTestId('simulation-error')
    expect(screen.getByTestId('simulation-error')).toHaveTextContent(/Simulation failed/i)
  })

  it('shows a patient-not-found message on 404', async () => {
    const apiPost = await getApiPost()
    apiPost.mockRejectedValueOnce(
      new ApiError(404, 'PATIENT_NOT_FOUND', 'Patient not found'),
    )
    const user = userEvent.setup()
    renderPanel()

    await user.type(screen.getByLabelText(/meal carbohydrates/i), '90')
    await user.click(screen.getByTestId('run-simulation-button'))

    await screen.findByTestId('simulation-error')
    expect(screen.getByTestId('simulation-error')).toHaveTextContent(
      /Patient or Digital Twin not found/i,
    )
  })

  it('shows an ML service unavailable message on 503', async () => {
    const apiPost = await getApiPost()
    apiPost.mockRejectedValueOnce(
      new ApiError(503, 'ML_SERVICE_UNAVAILABLE', 'ML service unavailable'),
    )
    const user = userEvent.setup()
    renderPanel()

    await user.type(screen.getByLabelText(/meal carbohydrates/i), '90')
    await user.click(screen.getByTestId('run-simulation-button'))

    await screen.findByTestId('simulation-error')
    expect(screen.getByTestId('simulation-error')).toHaveTextContent(
      /ML service is temporarily unavailable/i,
    )
  })

  it('shows an invalid input message on 400', async () => {
    const apiPost = await getApiPost()
    apiPost.mockRejectedValueOnce(
      new ApiError(400, 'VALIDATION_FAILED', 'mealCarbsGrams must be >= 0'),
    )
    const user = userEvent.setup()
    renderPanel()

    await user.type(screen.getByLabelText(/meal carbohydrates/i), '90')
    await user.click(screen.getByTestId('run-simulation-button'))

    await screen.findByTestId('simulation-error')
    expect(screen.getByTestId('simulation-error')).toHaveTextContent(/Invalid input/i)
  })

  it('does not show result card when API fails', async () => {
    const apiPost = await getApiPost()
    apiPost.mockRejectedValueOnce(new Error('Network error'))
    const user = userEvent.setup()
    renderPanel()

    await user.type(screen.getByLabelText(/meal carbohydrates/i), '90')
    await user.click(screen.getByTestId('run-simulation-button'))

    await screen.findByTestId('simulation-error')
    // The result heading should NOT appear
    expect(screen.queryByText('Simulation Result')).not.toBeInTheDocument()
  })

  // ── Reset / New Simulation ─────────────────────────────────────────────────

  it('shows the "New Simulation" button after a successful run', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_SIMULATION_RESPONSE)
    const user = userEvent.setup()
    renderPanel()

    await user.type(screen.getByLabelText(/meal carbohydrates/i), '90')
    await user.click(screen.getByTestId('run-simulation-button'))

    await screen.findByText(/New Simulation/i)
  })

  it('resets to the form when "New Simulation" is clicked', async () => {
    const apiPost = await getApiPost()
    apiPost.mockResolvedValueOnce(MOCK_SIMULATION_RESPONSE)
    const user = userEvent.setup()
    renderPanel()

    await user.type(screen.getByLabelText(/meal carbohydrates/i), '90')
    await user.click(screen.getByTestId('run-simulation-button'))
    await screen.findByText(/New Simulation/i)

    await user.click(screen.getByText(/New Simulation/i))

    // Form should be back
    await screen.findByTestId('simulation-form')
    expect(screen.queryByText('Simulation Result')).not.toBeInTheDocument()
  })
})
