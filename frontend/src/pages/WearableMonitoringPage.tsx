import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { usePatients } from '@/hooks/usePatients'
import { usePatientTwinState } from '@/hooks/usePatientDetail'
import { useCgmHistory } from '@/hooks/useCgmHistory'
import { api } from '@/lib/apiClient'
import { PatientContextSelector } from '@/components/common/PatientContextSelector'
import { SectionCard } from '@/components/common/SectionCard'
import { ProvenanceBadge } from '@/components/common/ProvenanceBadge'
import { LoadingSkeleton } from '@/components/common/LoadingSkeleton'
import { EmptyState } from '@/components/common/EmptyState'
import { IconHeartPulse, IconRefresh } from '@/components/common/Icons'
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

function fmtDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  try {
    return new Intl.DateTimeFormat('en-GB', {
      day: '2-digit',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(iso))
  } catch {
    return iso
  }
}

function fmtChartTime(iso: string): string {
  try {
    return new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit' }).format(
      new Date(iso)
    )
  } catch {
    return iso
  }
}

export default function WearableMonitoringPage() {
  const [searchParams] = useSearchParams()
  const queryClient = useQueryClient()
  const { data: patientsData } = usePatients()
  const patients = patientsData?.content ?? []

  const activePatientId =
    searchParams.get('patientId') || patients[0]?.patientId || ''

  const twinQuery = usePatientTwinState(activePatientId || undefined)
  const cgmQuery = useCgmHistory(activePatientId || undefined)

  const twin = twinQuery.data
  const dl = twin?.dynamicLayer
  const readings = [...(cgmQuery.data ?? [])].sort(
    (a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime()
  )

  // Wearable test ingestion form state
  const [ingestGlucose, setIngestGlucose] = useState('8.4')
  const [ingestHr, setIngestHr] = useState('76')
  const [ingestHrv, setIngestHrv] = useState('42')
  const [ingestSteps, setIngestSteps] = useState('5200')
  const [ingestActivity, setIngestActivity] = useState<'SEDENTARY' | 'LIGHT' | 'MODERATE' | 'VIGOROUS'>('MODERATE')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [ingestMsg, setIngestMsg] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  async function handleIngestEvent(e: React.FormEvent) {
    e.preventDefault()
    if (!activePatientId) return
    setIsSubmitting(true)
    setIngestMsg(null)

    try {
      const payload = {
        glucoseReading: parseFloat(ingestGlucose),
        heartRate: parseFloat(ingestHr),
        hrv: parseFloat(ingestHrv),
        sleepDuration: 7.2,
        sleepStage: 'LIGHT',
        stepCount: parseInt(ingestSteps, 10),
        activityLevel: ingestActivity,
        eventTimestamp: new Date().toISOString(),
      }

      await api.post(`/api/v1/patients/${activePatientId}/wearable-events`, payload)

      // Invalidate twin state and cgm history to reflect live event
      await queryClient.invalidateQueries({ queryKey: ['patients', activePatientId] })

      setIngestMsg({
        type: 'success',
        text: `Wearable telemetry event ingested (${ingestGlucose} mmol/L). Digital Twin state updated.`,
      })
      setIsSubmitting(false)
    } catch (err: unknown) {
      setIsSubmitting(false)
      setIngestMsg({
        type: 'error',
        text: err instanceof Error ? err.message : 'Failed to ingest event.',
      })
    }
  }

  const chartPoints = readings.map((r) => ({
    time: fmtChartTime(r.timestamp),
    isoTime: r.timestamp,
    value: r.value,
  }))

  const values = readings.map((r) => r.value)
  const latest = readings.length > 0 ? readings[readings.length - 1] : null
  const minVal = values.length > 0 ? Math.min(...values) : null
  const maxVal = values.length > 0 ? Math.max(...values) : null

  return (
    <div className="space-y-6">
      {/* Module Title */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-border pb-4">
        <div>
          <div className="flex items-center gap-2.5">
            <h2 className="text-xl font-bold tracking-tight text-text-primary">
              Wearable Continuous Telemetry Monitoring
            </h2>
            <ProvenanceBadge label="OBSERVED" />
          </div>
          <p className="mt-1 text-xs text-text-secondary">
            Continuous glucose monitoring (CGM) time series, autonomic tone metrics, and biometric activity telemetry.
          </p>
        </div>

        <button
          onClick={() => {
            void queryClient.invalidateQueries({ queryKey: ['patients', activePatientId] })
          }}
          className="btn-secondary text-xs"
          aria-label="Refresh telemetry buffer"
        >
          <IconRefresh size={14} className={cgmQuery.isFetching ? 'animate-spin' : ''} />
          Refresh Buffer
        </button>
      </div>

      {/* Patient Switcher */}
      <PatientContextSelector currentPatientId={activePatientId} />

      {/* Main Monitoring Section */}
      <div className="grid gap-6 lg:grid-cols-3">
        {/* Left 2 Cols: Continuous Glucose Timeline */}
        <div className="lg:col-span-2 space-y-6">
          <SectionCard
            title="Continuous Glucose Monitoring (CGM) Timeline"
            subtitle={`Rolling chronological telemetry for patient ${activePatientId}`}
            badge={<ProvenanceBadge label="OBSERVED" />}
          >
            {cgmQuery.isLoading ? (
              <LoadingSkeleton rows={3} height="h-28" />
            ) : chartPoints.length > 0 ? (
              <div className="space-y-4">
                {/* Metric Summary Tiles */}
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                  <div className="card p-3 border-border bg-card">
                    <span className="text-[10px] uppercase font-mono text-text-muted">Latest Observed</span>
                    <p className="text-base font-bold tabular-nums text-text-primary mt-0.5">
                      {latest ? `${latest.value.toFixed(1)} mmol/L` : '—'}
                    </p>
                  </div>
                  <div className="card p-3 border-border bg-card">
                    <span className="text-[10px] uppercase font-mono text-text-muted">Session Min</span>
                    <p className="text-base font-bold tabular-nums text-text-primary mt-0.5">
                      {minVal != null ? `${minVal.toFixed(1)} mmol/L` : '—'}
                    </p>
                  </div>
                  <div className="card p-3 border-border bg-card">
                    <span className="text-[10px] uppercase font-mono text-text-muted">Session Max</span>
                    <p className="text-base font-bold tabular-nums text-text-primary mt-0.5">
                      {maxVal != null ? `${maxVal.toFixed(1)} mmol/L` : '—'}
                    </p>
                  </div>
                  <div className="card p-3 border-border bg-card">
                    <span className="text-[10px] uppercase font-mono text-text-muted">Buffer Depth</span>
                    <p className="text-base font-bold tabular-nums text-text-primary mt-0.5">
                      {chartPoints.length} points
                    </p>
                  </div>
                </div>

                {/* Line Chart */}
                <div className="h-64 w-full" aria-label="CGM timeline chart" role="img">
                  <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={chartPoints} margin={{ top: 10, right: 10, left: -16, bottom: 0 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#222222" vertical={false} />
                      <XAxis
                        dataKey="time"
                        tick={{ fill: '#888888', fontSize: 11 }}
                        axisLine={{ stroke: '#222222' }}
                        tickLine={false}
                      />
                      <YAxis
                        domain={['auto', 'auto']}
                        tick={{ fill: '#888888', fontSize: 11 }}
                        axisLine={false}
                        tickLine={false}
                        unit=" mmol"
                      />
                      <Tooltip
                        contentStyle={{
                          backgroundColor: '#0A0A0A',
                          border: '1px solid #222222',
                          borderRadius: '8px',
                          color: '#EDEDED',
                          fontSize: '12px',
                        }}
                        formatter={(val: number) => [`${val.toFixed(1)} mmol/L`, 'Observed Glucose']}
                        labelFormatter={(_: string, payload: { payload?: { isoTime?: string } }[]) => {
                          const iso = payload?.[0]?.payload?.isoTime
                          return iso ? fmtDateTime(iso) : ''
                        }}
                      />
                      <Line
                        type="monotone"
                        dataKey="value"
                        stroke="#0070F3"
                        strokeWidth={2.5}
                        dot={<Dot r={3} fill="#0070F3" stroke="#000000" strokeWidth={1} />}
                        activeDot={{ r: 5, fill: '#0070F3' }}
                        isAnimationActive={false}
                      />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              </div>
            ) : (
              <EmptyState
                title="No Wearable Sensor Telemetry"
                description="Wearable CGM events will plot here as soon as events are ingested into the Digital Twin rolling buffer."
                icon={<IconHeartPulse size={24} />}
              />
            )}
          </SectionCard>

          {/* Vitals Summary Card */}
          {dl && (
            <SectionCard
              title="Autonomous Vitals & Activity Streams"
              subtitle="Cardiovascular and physical movement telemetry"
              badge={<ProvenanceBadge label="OBSERVED" />}
            >
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
                <div className="p-3 rounded-lg border border-border bg-card">
                  <span className="text-[10px] text-text-muted uppercase">Heart Rate</span>
                  <p className="text-base font-bold tabular-nums text-text-primary mt-0.5">
                    {dl.heartRate != null ? `${dl.heartRate} bpm` : '—'}
                  </p>
                </div>
                <div className="p-3 rounded-lg border border-border bg-card">
                  <span className="text-[10px] text-text-muted uppercase">HRV (SDNN)</span>
                  <p className="text-base font-bold tabular-nums text-text-primary mt-0.5">
                    {dl.hrv != null ? `${dl.hrv} ms` : '—'}
                  </p>
                </div>
                <div className="p-3 rounded-lg border border-border bg-card">
                  <span className="text-[10px] text-text-muted uppercase">Sleep Duration</span>
                  <p className="text-base font-bold tabular-nums text-text-primary mt-0.5">
                    {dl.sleepDuration != null ? `${dl.sleepDuration} hrs` : '—'}
                  </p>
                </div>
                <div className="p-3 rounded-lg border border-border bg-card">
                  <span className="text-[10px] text-text-muted uppercase">Step Count</span>
                  <p className="text-base font-bold tabular-nums text-text-primary mt-0.5">
                    {dl.stepCount != null ? dl.stepCount.toLocaleString() : '—'}
                  </p>
                </div>
              </div>
            </SectionCard>
          )}
        </div>

        {/* Right Col: Ingestion Testing & Event Simulator Tool */}
        <div className="space-y-6">
          <SectionCard
            title="Ingest Wearable Sensor Event"
            subtitle="Simulate real-time telemetry streaming directly to the backend"
          >
            <form onSubmit={handleIngestEvent} className="space-y-4 text-xs">
              {ingestMsg && (
                <div
                  className={`p-3 rounded-lg border text-[11px] leading-relaxed ${
                    ingestMsg.type === 'success'
                      ? 'border-status-success/40 bg-status-success/10 text-status-success'
                      : 'border-status-danger/40 bg-status-danger/10 text-status-danger'
                  }`}
                >
                  {ingestMsg.text}
                </div>
              )}

              <div>
                <label className="block text-[11px] font-medium text-text-secondary mb-1">
                  Glucose Reading (mmol/L)
                </label>
                <input
                  type="number"
                  step="0.1"
                  required
                  value={ingestGlucose}
                  onChange={(e) => setIngestGlucose(e.target.value)}
                  className="input-field w-full tabular-nums"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-[11px] font-medium text-text-secondary mb-1">
                    Heart Rate (bpm)
                  </label>
                  <input
                    type="number"
                    required
                    value={ingestHr}
                    onChange={(e) => setIngestHr(e.target.value)}
                    className="input-field w-full tabular-nums"
                  />
                </div>
                <div>
                  <label className="block text-[11px] font-medium text-text-secondary mb-1">
                    HRV (ms)
                  </label>
                  <input
                    type="number"
                    required
                    value={ingestHrv}
                    onChange={(e) => setIngestHrv(e.target.value)}
                    className="input-field w-full tabular-nums"
                  />
                </div>
              </div>

              <div>
                <label className="block text-[11px] font-medium text-text-secondary mb-1">
                  Step Count
                </label>
                <input
                  type="number"
                  required
                  value={ingestSteps}
                  onChange={(e) => setIngestSteps(e.target.value)}
                  className="input-field w-full tabular-nums"
                />
              </div>

              <div>
                <label className="block text-[11px] font-medium text-text-secondary mb-1">
                  Activity Level
                </label>
                <select
                  value={ingestActivity}
                  onChange={(e) => setIngestActivity(e.target.value as 'SEDENTARY' | 'LIGHT' | 'MODERATE' | 'VIGOROUS')}
                  className="input-field w-full cursor-pointer"
                >
                  <option value="SEDENTARY">Sedentary</option>
                  <option value="LIGHT">Light Activity</option>
                  <option value="MODERATE">Moderate Activity</option>
                  <option value="VIGOROUS">Vigorous Activity</option>
                </select>
              </div>

              <div className="rounded border border-border bg-bg-elevated/40 p-2.5 text-[10px] text-text-muted leading-relaxed">
                <strong className="text-text-secondary">API Workflow:</strong> Sends <code className="text-text-primary">POST /wearable-events</code> to the Spring Boot pipeline, triggering Redis event publishing and updating the Digital Twin state.
              </div>

              <button
                type="submit"
                disabled={isSubmitting || !activePatientId}
                className="btn-primary w-full text-xs"
              >
                {isSubmitting ? 'Publishing Event…' : 'Publish Wearable Event'}
              </button>
            </form>
          </SectionCard>
        </div>
      </div>
    </div>
  )
}
