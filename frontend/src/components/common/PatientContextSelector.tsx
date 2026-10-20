import { useSearchParams, useNavigate, Link } from 'react-router-dom'
import { usePatients } from '@/hooks/usePatients'
import { usePatientTwinState } from '@/hooks/usePatientDetail'
import { StatusBadge } from './StatusBadge'
import { IconUsers, IconArrowRight } from './Icons'

interface PatientContextSelectorProps {
  onPatientChange?: (patientId: string) => void
  currentPatientId?: string
}

export function isSyntheticPatient(patientId: string): boolean {
  return (
    patientId.toLowerCase().startsWith('e1000000-') ||
    patientId.toLowerCase() === '00000000-0000-0000-0000-000000000001'
  )
}

export function PatientContextSelector({
  onPatientChange,
  currentPatientId,
}: PatientContextSelectorProps) {
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()
  const { data: patientsData, isLoading } = usePatients()

  const patients = patientsData?.content ?? []
  const activeId =
    currentPatientId ||
    searchParams.get('patientId') ||
    patients[0]?.patientId ||
    ''

  const twinQuery = usePatientTwinState(activeId || undefined)
  const isSynthetic = isSyntheticPatient(activeId)

  function handleSelect(newId: string) {
    if (!newId) return
    const params = new URLSearchParams(searchParams)
    params.set('patientId', newId)
    setSearchParams(params)
    if (onPatientChange) {
      onPatientChange(newId)
    }
  }

  return (
    <div className="card p-3.5 mb-6 flex flex-wrap items-center justify-between gap-4 border-border bg-card">
      <div className="flex flex-wrap items-center gap-3 min-w-0">
        <div className="flex items-center gap-2 text-xs text-text-muted flex-shrink-0">
          <IconUsers size={14} className="text-text-secondary" />
          <span className="uppercase font-mono tracking-wider font-semibold text-[10px]">
            Active Patient Context:
          </span>
        </div>

        {/* Patient Dropdown Switcher */}
        <div className="relative min-w-[280px] sm:min-w-[340px]">
          <select
            value={activeId}
            onChange={(e) => handleSelect(e.target.value)}
            disabled={isLoading || patients.length === 0}
            className="input-field w-full py-1.5 pl-3 pr-8 text-xs font-mono font-medium cursor-pointer"
            aria-label="Select active clinical patient context"
          >
            {patients.map((p) => {
              const synth = isSyntheticPatient(p.patientId)
              return (
                <option key={p.patientId} value={p.patientId}>
                  {p.patientId} [{synth ? 'SYNTHETIC' : 'ENROLLED'}]
                </option>
              )
            })}
          </select>
        </div>

        {/* Provenance Badge */}
        {activeId && (
          <span
            className={`rounded px-2 py-0.5 font-mono text-[10px] font-semibold tracking-wider border ${
              isSynthetic
                ? 'border-border-hover bg-bg-elevated text-text-secondary'
                : 'border-status-info/40 bg-status-info/10 text-status-info'
            }`}
          >
            {isSynthetic ? 'SYNTHETIC RESEARCH DATA' : 'USER-ENROLLED CLINICAL'}
          </span>
        )}

        {/* Twin Status */}
        {twinQuery.data && (
          <StatusBadge status={twinQuery.data.status} />
        )}
      </div>

      {/* Quick Navigation to Full Detail */}
      <div className="flex items-center gap-2.5 text-xs">
        {activeId && (
          <button
            onClick={() => navigate(`/patients/${activeId}`)}
            className="btn-secondary py-1 px-2.5 text-[11px]"
            title="Open comprehensive 7-tier patient workspace"
          >
            Full Digital Twin Workspace <IconArrowRight size={12} />
          </button>
        )}
        <Link
          to="/patients"
          className="text-[11px] text-text-secondary hover:text-text-primary transition-colors underline"
        >
          All Patients
        </Link>
      </div>
    </div>
  )
}
