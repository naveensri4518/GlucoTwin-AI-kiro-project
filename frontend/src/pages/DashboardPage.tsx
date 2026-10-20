import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { usePatients } from '@/hooks/usePatients'
import { usePatientTwinState } from '@/hooks/usePatientDetail'
import { usePatientPredictions } from '@/hooks/usePredictions'
import { useCgmHistory } from '@/hooks/useCgmHistory'
import { StatCard } from '@/components/common/StatCard'
import { SectionCard } from '@/components/common/SectionCard'
import { ProvenanceBadge } from '@/components/common/ProvenanceBadge'
import { LoadingSkeleton } from '@/components/common/LoadingSkeleton'
import { EmptyState } from '@/components/common/EmptyState'
import { isSyntheticPatient } from '@/components/common/PatientContextSelector'
import {
  IconUsers,
  IconBrain,
  IconTwin,
  IconHeartPulse,
  IconSliders,
  IconSparkles,
  IconArrowRight,
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

export default function DashboardPage() {
  const navigate = useNavigate()

  // 1. Load registered patient cohort
  const patientsQuery = usePatients(0, 50)
  const patients = patientsQuery.data?.content ?? []
  const totalPatients =
    patientsQuery.data?.totalElements ??
    patientsQuery.data?.page?.totalElements ??
    patients.length

  const syntheticCount = patients.filter((p) => isSyntheticPatient(p.patientId)).length
  const userEnrolledCount = totalPatients - syntheticCount

  // Active patient for cohort trend preview
  const [selectedPatientId, setSelectedPatientId] = useState<string>('')
  const activePatientId = selectedPatientId || patients[0]?.patientId || ''

  // 2. Load telemetry for selected patient
  const twinQuery = usePatientTwinState(activePatientId || undefined)
  const predictionsQuery = usePatientPredictions(activePatientId || undefined, 5)
  const cgmQuery = useCgmHistory(activePatientId || undefined)

  const twin = twinQuery.data
  const latestPrediction = predictionsQuery.data?.content?.[0]
  const cgmReadings = cgmQuery.data ?? []

  const sortedCgm = [...cgmReadings].sort(
    (a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime()
  )

  const chartData = sortedCgm.map((r) => {
    let timeLabel = r.timestamp
    try {
      timeLabel = new Intl.DateTimeFormat('en-GB', {
        hour: '2-digit',
        minute: '2-digit',
      }).format(new Date(r.timestamp))
    } catch {
      // fallback
    }
    return {
      time: timeLabel,
      iso: r.timestamp,
      value: r.value,
    }
  })

  return (
    <div className="space-y-6">
      {/* Page Title & Clinical Subtitle */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-border pb-4">
        <div>
          <h2 className="text-xl font-bold tracking-tight text-text-primary">
            Clinical Overview & Cohort Intelligence
          </h2>
          <p className="mt-1 text-xs text-text-secondary">
            Continuous glucose physiological modeling, predictive spike forecasts, and multi-patient Digital Twin cohorts.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Link to="/patients" className="btn-primary text-xs">
            <IconUsers size={14} />
            Patient Registry ({totalPatients})
          </Link>
        </div>
      </div>

      {/* ── TOP COHORT METRICS (Real PostgreSQL data) ── */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {/* Metric 1: Total Enrolled Patients */}
        <StatCard
          label="Registered Patients"
          value={patientsQuery.isLoading ? '…' : totalPatients}
          unit="cohort"
          subtitle={`${userEnrolledCount} Clinical Enrolled · ${syntheticCount} Synthetic Research`}
          icon={<IconUsers size={16} />}
          highlight={totalPatients > 0}
        />

        {/* Metric 2: Active Digital Twins */}
        <StatCard
          label="Active Digital Twins"
          value={
            twinQuery.isLoading
              ? '…'
              : twin?.status
              ? twin.status
              : totalPatients > 0
              ? 'ACTIVE'
              : '0'
          }
          unit={twin ? `v${twin.twinVersion}` : undefined}
          subtitle={`Current Focus: ${activePatientId ? activePatientId.slice(0, 8) + '…' : 'None'}`}
          icon={<IconTwin size={16} />}
          statusIndicator={twin?.status === 'ACTIVE' ? 'success' : undefined}
        />

        {/* Metric 3: Latest 2-Hr Spike Risk */}
        <StatCard
          label="2-Hr Spike Risk"
          value={
            predictionsQuery.isLoading
              ? '…'
              : latestPrediction?.spikeProbability != null
              ? `${(latestPrediction.spikeProbability * 100).toFixed(1)}%`
              : 'N/A'
          }
          provenance="PREDICTED"
          subtitle={
            latestPrediction?.riskCategory
              ? `Category: ${latestPrediction.riskCategory} · Model: ${latestPrediction.modelVersion}`
              : 'Execute prediction run'
          }
          icon={<IconBrain size={16} />}
          statusIndicator={
            latestPrediction?.riskCategory === 'HIGH' || latestPrediction?.riskCategory === 'CRITICAL'
              ? 'danger'
              : latestPrediction?.riskCategory === 'MODERATE'
              ? 'warning'
              : latestPrediction?.riskCategory === 'LOW'
              ? 'success'
              : undefined
          }
        />

        {/* Metric 4: Latest Observed Glucose */}
        <StatCard
          label="Observed Glucose"
          value={
            twinQuery.isLoading
              ? '…'
              : twin?.dynamicLayer?.glucoseReading != null
              ? twin.dynamicLayer.glucoseReading.toFixed(1)
              : '—'
          }
          unit={twin?.dynamicLayer?.glucoseReading != null ? 'mmol/L' : undefined}
          provenance="OBSERVED"
          subtitle={
            twin?.dynamicLayer?.heartRate != null
              ? `HR: ${twin.dynamicLayer.heartRate} bpm · Buffer: ${twin.dynamicLayer.cgmHistorySize} pts`
              : 'Awaiting wearable stream'
          }
          icon={<IconHeartPulse size={16} />}
        />
      </div>

      {/* ── CLINICAL MODULE SHORTCUT WORKSPACES ── */}
      <div className="grid grid-cols-2 sm:grid-cols-5 gap-3">
        <Link
          to={`/predictions${activePatientId ? `?patientId=${activePatientId}` : ''}`}
          className="card p-3.5 hover:border-text-secondary transition-all group"
        >
          <div className="flex items-center justify-between text-text-muted group-hover:text-text-primary mb-2">
            <IconBrain size={16} />
            <IconArrowRight size={12} className="opacity-0 group-hover:opacity-100 transition-opacity" />
          </div>
          <p className="text-xs font-semibold text-text-primary">Predictions</p>
          <p className="text-[10px] text-text-muted mt-0.5">2-Hr spike forecasting</p>
        </Link>

        <Link
          to={`/insights${activePatientId ? `?patientId=${activePatientId}` : ''}`}
          className="card p-3.5 hover:border-text-secondary transition-all group"
        >
          <div className="flex items-center justify-between text-text-muted group-hover:text-text-primary mb-2">
            <IconSparkles size={16} />
            <IconArrowRight size={12} className="opacity-0 group-hover:opacity-100 transition-opacity" />
          </div>
          <p className="text-xs font-semibold text-text-primary">Clinical Insights</p>
          <p className="text-[10px] text-text-muted mt-0.5">Evidence reasoning</p>
        </Link>

        <Link
          to={`/twin${activePatientId ? `?patientId=${activePatientId}` : ''}`}
          className="card p-3.5 hover:border-text-secondary transition-all group"
        >
          <div className="flex items-center justify-between text-text-muted group-hover:text-text-primary mb-2">
            <IconTwin size={16} />
            <IconArrowRight size={12} className="opacity-0 group-hover:opacity-100 transition-opacity" />
          </div>
          <p className="text-xs font-semibold text-text-primary">Digital Twin</p>
          <p className="text-[10px] text-text-muted mt-0.5">Physiological state</p>
        </Link>

        <Link
          to={`/wearables${activePatientId ? `?patientId=${activePatientId}` : ''}`}
          className="card p-3.5 hover:border-text-secondary transition-all group"
        >
          <div className="flex items-center justify-between text-text-muted group-hover:text-text-primary mb-2">
            <IconHeartPulse size={16} />
            <IconArrowRight size={12} className="opacity-0 group-hover:opacity-100 transition-opacity" />
          </div>
          <p className="text-xs font-semibold text-text-primary">Wearable Telemetry</p>
          <p className="text-[10px] text-text-muted mt-0.5">CGM & vital streams</p>
        </Link>

        <Link
          to={`/simulations${activePatientId ? `?patientId=${activePatientId}` : ''}`}
          className="card p-3.5 hover:border-text-secondary transition-all group"
        >
          <div className="flex items-center justify-between text-text-muted group-hover:text-text-primary mb-2">
            <IconSliders size={16} />
            <IconArrowRight size={12} className="opacity-0 group-hover:opacity-100 transition-opacity" />
          </div>
          <p className="text-xs font-semibold text-text-primary">Simulations</p>
          <p className="text-[10px] text-text-muted mt-0.5">What-if scenarios</p>
        </Link>
      </div>

      {/* ── GLUCOSE OBSERVATION TREND & PATIENT WORKSPACE ── */}
      <div className="space-y-6">
        {/* Glucose Timeline */}
        <SectionCard
          title="Glucose Observation Telemetry Curve"
          subtitle={`Displaying wearable continuous sensor data for patient ${activePatientId}`}
          badge={<ProvenanceBadge label="OBSERVED" />}
          action={
            <div className="flex items-center gap-2">
              <select
                value={activePatientId}
                onChange={(e) => setSelectedPatientId(e.target.value)}
                className="input-field py-1 text-xs font-mono cursor-pointer"
                aria-label="Select patient for telemetry curve"
              >
                {patients.map((p) => (
                  <option key={p.patientId} value={p.patientId}>
                    {p.patientId.slice(0, 8)}… [{isSyntheticPatient(p.patientId) ? 'SYNTHETIC' : 'ENROLLED'}]
                  </option>
                ))}
              </select>
            </div>
          }
        >
          {cgmQuery.isLoading ? (
            <LoadingSkeleton rows={2} height="h-28" />
          ) : chartData.length > 0 ? (
            <div>
              <div className="mb-4 flex flex-wrap items-center justify-between gap-2 text-xs">
                <div className="flex items-center gap-4">
                  <div>
                    <span className="text-text-muted">Latest Value: </span>
                    <span className="font-semibold text-text-primary font-mono">
                      {chartData[chartData.length - 1].value.toFixed(1)} mmol/L
                    </span>
                  </div>
                  <div>
                    <span className="text-text-muted">Observed Range: </span>
                    <span className="font-mono text-text-secondary">
                      {Math.min(...chartData.map((d) => d.value)).toFixed(1)} –{' '}
                      {Math.max(...chartData.map((d) => d.value)).toFixed(1)} mmol/L
                    </span>
                  </div>
                </div>
                <span className="rounded bg-elevated px-2 py-0.5 font-mono text-[10px] text-text-secondary border border-border">
                  {chartData.length} Telemetry Points Stored
                </span>
              </div>

              <div className="h-60 w-full" aria-label="Glucose trend chart" role="img">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={chartData} margin={{ top: 8, right: 12, left: -16, bottom: 0 }}>
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
                        borderColor: '#222222',
                        borderRadius: '8px',
                        color: '#EDEDED',
                        fontSize: '12px',
                      }}
                      formatter={(val: number) => [`${val.toFixed(1)} mmol/L`, 'Observed Glucose']}
                      labelFormatter={(_: string, payload: { payload?: { iso?: string } }[]) => {
                        const iso = payload?.[0]?.payload?.iso
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
              title="No Sensor Readings for This Patient"
              description="Select another patient or navigate to Wearable Monitoring to ingest a continuous sensor event."
            />
          )}
        </SectionCard>

        {/* Patient Registry Quick Table */}
        <SectionCard
          title="Patient Registry & Quick Module Access"
          subtitle="Direct links into patient digital twin workspaces"
          action={
            <Link to="/patients" className="btn-secondary text-xs">
              View All Patients ({totalPatients})
            </Link>
          }
        >
          {patientsQuery.isLoading ? (
            <LoadingSkeleton rows={4} height="h-12" />
          ) : patients.length === 0 ? (
            <EmptyState
              title="No Registered Patients"
              description="Enroll a patient or run the synthetic patient seeder to populate cohort records."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs" aria-label="Enrolled patients summary">
                <thead>
                  <tr className="border-b border-border text-text-muted font-mono text-[11px] uppercase tracking-wider">
                    <th className="pb-2.5 font-medium">Patient Identifier</th>
                    <th className="pb-2.5 font-medium">Type</th>
                    <th className="pb-2.5 font-medium">Enrolled Date</th>
                    <th className="pb-2.5 text-right font-medium">Module Shortcuts</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/60">
                  {patients.slice(0, 7).map((p) => {
                    const isSynth = isSyntheticPatient(p.patientId)
                    return (
                      <tr key={p.patientId} className="hover:bg-elevated/50 transition-colors">
                        <td className="py-3 font-mono text-text-primary">
                          <Link
                            to={`/patients/${p.patientId}`}
                            className="hover:underline flex items-center gap-2"
                          >
                            <span className="flex h-6 w-6 items-center justify-center rounded bg-elevated border border-border text-text-secondary text-[11px] font-bold">
                              {p.patientId.slice(0, 2).toUpperCase()}
                            </span>
                            <span className="truncate max-w-[200px] sm:max-w-xs">{p.patientId}</span>
                          </Link>
                        </td>
                        <td className="py-3">
                          <span
                            className={`rounded px-1.5 py-0.5 text-[9px] font-mono border ${
                              isSynth
                                ? 'border-border bg-subtle text-text-muted'
                                : 'border-status-info/40 bg-status-info/10 text-status-info font-semibold'
                            }`}
                          >
                            {isSynth ? 'SYNTHETIC' : 'CLINICAL ENROLLED'}
                          </span>
                        </td>
                        <td className="py-3 text-text-secondary font-mono text-[11px]">
                          {fmtDateTime(p.createdAt)}
                        </td>
                        <td className="py-3 text-right">
                          <div className="inline-flex items-center gap-1.5">
                            <button
                              onClick={() => navigate(`/predictions?patientId=${p.patientId}`)}
                              className="rounded px-2 py-1 text-[10px] font-mono bg-elevated hover:bg-card border border-border text-text-secondary hover:text-text-primary transition-colors"
                              title="Predict"
                            >
                              Predict
                            </button>
                            <button
                              onClick={() => navigate(`/insights?patientId=${p.patientId}`)}
                              className="rounded px-2 py-1 text-[10px] font-mono bg-elevated hover:bg-card border border-border text-text-secondary hover:text-text-primary transition-colors"
                              title="Insights"
                            >
                              Insights
                            </button>
                            <button
                              onClick={() => navigate(`/simulations?patientId=${p.patientId}`)}
                              className="rounded px-2 py-1 text-[10px] font-mono bg-elevated hover:bg-card border border-border text-text-secondary hover:text-text-primary transition-colors"
                              title="Simulate"
                            >
                              Simulate
                            </button>
                            <button
                              onClick={() => navigate(`/patients/${p.patientId}`)}
                              className="rounded px-2.5 py-1 text-[10px] font-semibold bg-text-primary text-black hover:bg-white transition-colors"
                            >
                              Open Twin →
                            </button>
                          </div>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </SectionCard>
      </div>
    </div>
  )
}
