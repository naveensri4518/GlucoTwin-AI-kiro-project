import { useSearchParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { usePatients } from '@/hooks/usePatients'
import { usePatientEhr, usePatientTwinState } from '@/hooks/usePatientDetail'
import { PatientContextSelector } from '@/components/common/PatientContextSelector'
import { SectionCard } from '@/components/common/SectionCard'
import { StatusBadge } from '@/components/common/StatusBadge'
import { ProvenanceBadge } from '@/components/common/ProvenanceBadge'
import { LoadingSkeleton } from '@/components/common/LoadingSkeleton'
import { IconRefresh, IconCheckCircle } from '@/components/common/Icons'

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

export default function DigitalTwinPage() {
  const [searchParams] = useSearchParams()
  const queryClient = useQueryClient()
  const { data: patientsData } = usePatients()
  const patients = patientsData?.content ?? []

  const activePatientId =
    searchParams.get('patientId') || patients[0]?.patientId || ''

  const ehrQuery = usePatientEhr(activePatientId || undefined)
  const twinQuery = usePatientTwinState(activePatientId || undefined)

  const twin = twinQuery.data
  const ehr = ehrQuery.data
  const dl = twin?.dynamicLayer

  function handleRefresh() {
    void queryClient.invalidateQueries({ queryKey: ['patients', activePatientId] })
  }

  return (
    <div className="space-y-6">
      {/* Title */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-border pb-4">
        <div>
          <div className="flex items-center gap-2.5">
            <h2 className="text-xl font-bold tracking-tight text-text-primary">
              Physiological Digital Twin State
            </h2>
            <ProvenanceBadge label="OBSERVED" />
          </div>
          <p className="mt-1 text-xs text-text-secondary">
            Computational metabolic digital twin synchronizing static clinical EHR records and continuous dynamic wearable streams.
          </p>
        </div>

        <button
          onClick={handleRefresh}
          className="btn-secondary text-xs"
          aria-label="Refresh Digital Twin State"
        >
          <IconRefresh size={14} className={twinQuery.isFetching ? 'animate-spin' : ''} />
          Synchronize State
        </button>
      </div>

      {/* Patient Switcher */}
      <PatientContextSelector currentPatientId={activePatientId} />

      {/* Digital Twin State Architecture Summary */}
      <div className="grid gap-6 lg:grid-cols-3">
        {/* Left: Twin Overview & Versioning */}
        <div className="space-y-6 lg:col-span-2">
          {/* Metadata Card */}
          <SectionCard
            title="Digital Twin Specification"
            subtitle={`Patient ID: ${activePatientId}`}
            action={twin && <StatusBadge status={twin.status} />}
          >
            {twinQuery.isLoading ? (
              <LoadingSkeleton rows={3} height="h-16" />
            ) : twin ? (
              <div className="space-y-4">
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
                  <div className="card p-3 border-border bg-card">
                    <span className="text-[10px] font-mono uppercase text-text-muted">Twin Version</span>
                    <p className="text-lg font-bold font-mono text-text-primary mt-1">v{twin.twinVersion}</p>
                  </div>
                  <div className="card p-3 border-border bg-card">
                    <span className="text-[10px] font-mono uppercase text-text-muted">State Status</span>
                    <div className="mt-1.5"><StatusBadge status={twin.status} /></div>
                  </div>
                  <div className="card p-3 border-border bg-card">
                    <span className="text-[10px] font-mono uppercase text-text-muted">Created</span>
                    <p className="text-xs font-mono text-text-primary mt-1">{fmtDateTime(twin.createdAt)}</p>
                  </div>
                  <div className="card p-3 border-border bg-card">
                    <span className="text-[10px] font-mono uppercase text-text-muted">Last Synchronized</span>
                    <p className="text-xs font-mono text-text-primary mt-1">{fmtDateTime(twin.lastUpdatedAt)}</p>
                  </div>
                </div>

                {/* Dynamic Layer Biometrics */}
                <div className="border-t border-border pt-4">
                  <div className="flex items-center justify-between mb-3">
                    <h4 className="text-xs font-semibold uppercase tracking-wider text-text-secondary">
                      Dynamic Physiological Layer (Telemetry)
                    </h4>
                    <ProvenanceBadge label="OBSERVED" />
                  </div>

                  {dl ? (
                    <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 text-xs">
                      <div className="p-3 rounded-lg border border-border bg-elevated">
                        <span className="text-[10px] text-text-muted uppercase">Latest Glucose</span>
                        <p className="text-base font-bold tabular-nums text-text-primary mt-0.5">
                          {dl.glucoseReading != null ? `${dl.glucoseReading.toFixed(1)} mmol/L` : '—'}
                        </p>
                      </div>
                      <div className="p-3 rounded-lg border border-border bg-elevated">
                        <span className="text-[10px] text-text-muted uppercase">Heart Rate</span>
                        <p className="text-base font-bold tabular-nums text-text-primary mt-0.5">
                          {fmtNum(dl.heartRate, 0, 'bpm')}
                        </p>
                      </div>
                      <div className="p-3 rounded-lg border border-border bg-elevated">
                        <span className="text-[10px] text-text-muted uppercase">HRV</span>
                        <p className="text-base font-bold tabular-nums text-text-primary mt-0.5">
                          {fmtNum(dl.hrv, 0, 'ms')}
                        </p>
                      </div>
                      <div className="p-3 rounded-lg border border-border bg-elevated">
                        <span className="text-[10px] text-text-muted uppercase">Sleep Duration</span>
                        <p className="text-base font-bold tabular-nums text-text-primary mt-0.5">
                          {fmtNum(dl.sleepDuration, 1, 'hrs')} ({dl.sleepStage ?? '—'})
                        </p>
                      </div>
                      <div className="p-3 rounded-lg border border-border bg-elevated">
                        <span className="text-[10px] text-text-muted uppercase">Step Count</span>
                        <p className="text-base font-bold tabular-nums text-text-primary mt-0.5">
                          {dl.stepCount != null ? dl.stepCount.toLocaleString() : '—'}
                        </p>
                      </div>
                      <div className="p-3 rounded-lg border border-border bg-elevated">
                        <span className="text-[10px] text-text-muted uppercase">CGM Buffer Depth</span>
                        <p className="text-base font-bold tabular-nums text-text-primary mt-0.5">
                          {dl.cgmHistorySize} points
                        </p>
                      </div>
                    </div>
                  ) : (
                    <p className="text-xs text-text-muted py-2">
                      Twin is in INITIALISED state. Awaiting streaming biometric events.
                    </p>
                  )}
                </div>

                {/* Static EHR Layer */}
                <div className="border-t border-border pt-4">
                  <div className="flex items-center justify-between mb-3">
                    <h4 className="text-xs font-semibold uppercase tracking-wider text-text-secondary">
                      Static Baseline Layer (EHR Clinical Record)
                    </h4>
                    <ProvenanceBadge label="OBSERVED" />
                  </div>

                  {ehr ? (
                    <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 text-xs">
                      <div className="p-3 rounded-lg border border-border bg-card">
                        <span className="text-[10px] text-text-muted uppercase">DOB & Biological Sex</span>
                        <p className="text-sm font-semibold text-text-primary mt-0.5">
                          {ehr.dateOfBirth} ({ehr.sex})
                        </p>
                      </div>
                      <div className="p-3 rounded-lg border border-border bg-card">
                        <span className="text-[10px] text-text-muted uppercase">BMI Index</span>
                        <p className="text-sm font-semibold text-text-primary mt-0.5">
                          {fmtNum(ehr.bmi, 1, 'kg/m²')}
                        </p>
                      </div>
                      <div className="p-3 rounded-lg border border-border bg-card">
                        <span className="text-[10px] text-text-muted uppercase">Diagnosis Onset</span>
                        <p className="text-sm font-semibold text-text-primary mt-0.5">
                          {ehr.diabetesOnsetDate}
                        </p>
                      </div>
                      <div className="p-3 rounded-lg border border-border bg-card">
                        <span className="text-[10px] text-text-muted uppercase">Baseline HbA1c</span>
                        <p className="text-sm font-semibold text-text-primary mt-0.5">
                          {fmtNum(ehr.hba1c, 1, '%')}
                        </p>
                      </div>
                      <div className="p-3 rounded-lg border border-border bg-card">
                        <span className="text-[10px] text-text-muted uppercase">Fasting Glucose</span>
                        <p className="text-sm font-semibold text-text-primary mt-0.5">
                          {fmtNum(ehr.fastingGlucose, 1, 'mmol/L')}
                        </p>
                      </div>
                    </div>
                  ) : (
                    <p className="text-xs text-text-muted py-2">No EHR baseline ingested.</p>
                  )}
                </div>
              </div>
            ) : (
              <p className="text-xs text-text-muted py-6 text-center">No Digital Twin record found for this patient.</p>
            )}
          </SectionCard>
        </div>

        {/* Right: State Semantics & Architecture Guidance */}
        <div className="space-y-6">
          <SectionCard
            title="State Status Lexicon"
            subtitle="Clinical definitions of Twin lifecycle states"
          >
            <div className="space-y-3 text-xs">
              <div className="p-3 rounded-lg border border-border bg-card">
                <div className="flex items-center gap-2 mb-1">
                  <StatusBadge status="ACTIVE" />
                  <span className="font-semibold text-text-primary">Operational Stream</span>
                </div>
                <p className="text-[11px] text-text-secondary leading-relaxed">
                  Both static EHR and recent dynamic wearable telemetry are present. Model prediction features are fully computable.
                </p>
              </div>

              <div className="p-3 rounded-lg border border-border bg-card">
                <div className="flex items-center gap-2 mb-1">
                  <StatusBadge status="INITIALISED" />
                  <span className="font-semibold text-text-primary">EHR Only</span>
                </div>
                <p className="text-[11px] text-text-secondary leading-relaxed">
                  Static clinical baseline is loaded. Awaiting first wearable sensor event before physiological predictions can trigger.
                </p>
              </div>

              <div className="p-3 rounded-lg border border-border bg-card">
                <div className="flex items-center gap-2 mb-1">
                  <StatusBadge status="STALE" />
                  <span className="font-semibold text-text-primary">Telemetry Gap</span>
                </div>
                <p className="text-[11px] text-text-secondary leading-relaxed">
                  Wearable events have not been received within the expected telemetry window. Predictions may have wider confidence intervals.
                </p>
              </div>
            </div>
          </SectionCard>

          <SectionCard
            title="State Invariants"
            subtitle="Architectural integrity rules"
          >
            <div className="space-y-2.5 text-xs text-text-secondary">
              <div className="flex items-start gap-2">
                <IconCheckCircle size={14} className="text-status-success flex-shrink-0 mt-0.5" />
                <span>Optimistic locking with automated version bump on each event.</span>
              </div>
              <div className="flex items-start gap-2">
                <IconCheckCircle size={14} className="text-status-success flex-shrink-0 mt-0.5" />
                <span>Counterfactual simulations never overwrite observed twin parameters.</span>
              </div>
              <div className="flex items-start gap-2">
                <IconCheckCircle size={14} className="text-status-success flex-shrink-0 mt-0.5" />
                <span>Rolling CGM buffer strictly constrained to chronological order.</span>
              </div>
            </div>
          </SectionCard>
        </div>
      </div>
    </div>
  )
}
