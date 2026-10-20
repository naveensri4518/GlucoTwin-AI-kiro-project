import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { usePatients, PATIENTS_QUERY_KEY } from '@/hooks/usePatients'
import { usePatientTwinState } from '@/hooks/usePatientDetail'
import type { PatientSummary } from '@/types/patient'
import { StatusBadge } from '@/components/common/StatusBadge'
import { ProvenanceBadge } from '@/components/common/ProvenanceBadge'
import { LoadingSkeleton } from '@/components/common/LoadingSkeleton'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { EnrollPatientModal } from '@/components/common/EnrollPatientModal'
import { isSyntheticPatient } from '@/components/common/PatientContextSelector'
import {
  IconSearch,
  IconRefresh,
  IconUsers,
  IconArrowRight,
} from '@/components/common/Icons'

function formatDate(iso: string): string {
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

function shortId(uuid: string): string {
  return uuid.split('-')[0] ?? uuid
}

/** Individual table row fetching live twin metadata for accurate clinical display */
function PatientTableRow({
  patient,
  onSelect,
}: {
  patient: PatientSummary
  onSelect: (id: string) => void
}) {
  const navigate = useNavigate()
  const twinQuery = usePatientTwinState(patient.patientId)
  const twin = twinQuery.data
  const glucose = twin?.dynamicLayer?.glucoseReading
  const isSynth = isSyntheticPatient(patient.patientId)

  return (
    <tr
      onClick={() => onSelect(patient.patientId)}
      className="cursor-pointer border-b border-border/60 hover:bg-elevated/50 transition-colors group"
      role="row"
    >
      {/* Patient Identifier */}
      <td className="py-4 px-4 font-mono text-xs">
        <div className="flex items-center gap-3">
          <div className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded bg-elevated text-text-primary font-bold text-xs border border-border group-hover:border-text-secondary transition-colors">
            {shortId(patient.patientId).slice(0, 2).toUpperCase()}
          </div>
          <div>
            <span className="font-semibold text-text-primary group-hover:underline block truncate max-w-[200px] sm:max-w-xs md:max-w-md">
              {patient.patientId}
            </span>
            <span className="text-[10px] text-text-muted">
              UUID #{shortId(patient.patientId)}
            </span>
          </div>
        </div>
      </td>

      {/* Provenance Category */}
      <td className="py-4 px-4">
        <span
          className={`rounded px-2 py-0.5 text-[9px] font-mono border ${
            isSynth
              ? 'border-border bg-subtle text-text-secondary'
              : 'border-status-info/40 bg-status-info/10 text-status-info font-semibold'
          }`}
        >
          {isSynth ? 'SYNTHETIC RESEARCH' : 'USER-ENROLLED'}
        </span>
      </td>

      {/* Digital Twin Status */}
      <td className="py-4 px-4">
        {twinQuery.isLoading ? (
          <span className="text-[11px] text-text-muted font-mono">Syncing…</span>
        ) : (
          <StatusBadge status={twin?.status ?? 'INITIALISED'} />
        )}
      </td>

      {/* Latest Observed Glucose */}
      <td className="py-4 px-4">
        {glucose != null ? (
          <div className="flex items-center gap-1.5 font-mono text-xs">
            <span className="font-semibold text-text-primary tabular-nums">
              {glucose.toFixed(1)} mmol/L
            </span>
            <ProvenanceBadge label="OBSERVED" size="sm" />
          </div>
        ) : (
          <span className="text-[11px] text-text-muted">—</span>
        )}
      </td>

      {/* Enrolled Timestamp */}
      <td className="py-4 px-4 text-xs text-text-secondary font-mono text-[11px]">
        <time dateTime={patient.createdAt}>{formatDate(patient.createdAt)}</time>
      </td>

      {/* Actions */}
      <td className="py-4 px-4 text-right">
        <div className="inline-flex items-center gap-2" onClick={(e) => e.stopPropagation()}>
          <button
            onClick={() => navigate(`/predictions?patientId=${patient.patientId}`)}
            className="rounded px-2 py-1 text-[10px] font-mono bg-elevated hover:bg-card border border-border text-text-secondary hover:text-text-primary transition-colors"
            title="Open Prediction module"
          >
            Predict
          </button>
          <button
            onClick={() => onSelect(patient.patientId)}
            className="btn-primary text-xs py-1 px-3"
            aria-label={`Open Digital Twin workspace for ${patient.patientId}`}
          >
            Open Twin <IconArrowRight size={12} />
          </button>
        </div>
      </td>
    </tr>
  )
}

export default function PatientsPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [filterText, setFilterText] = useState('')
  const [categoryFilter, setCategoryFilter] = useState<'ALL' | 'ENROLLED' | 'SYNTHETIC'>('ALL')
  const [sortOrder, setSortOrder] = useState<'desc' | 'asc'>('desc')
  const [isEnrollModalOpen, setIsEnrollModalOpen] = useState(false)

  const { data, isLoading, isError, error, isFetching, refetch } = usePatients(0, 50)

  const rawPatients = data?.content ?? []

  // Filter by category
  let filtered = rawPatients.filter((p) => {
    const isSynth = isSyntheticPatient(p.patientId)
    if (categoryFilter === 'ENROLLED') return !isSynth
    if (categoryFilter === 'SYNTHETIC') return isSynth
    return true
  })

  // Filter by search query
  if (filterText.trim()) {
    filtered = filtered.filter((p) =>
      p.patientId.toLowerCase().includes(filterText.trim().toLowerCase())
    )
  }

  // Sort by date
  filtered = [...filtered].sort((a, b) => {
    const timeA = new Date(a.createdAt).getTime()
    const timeB = new Date(b.createdAt).getTime()
    return sortOrder === 'desc' ? timeB - timeA : timeA - timeB
  })

  const totalElements =
    data?.totalElements ?? data?.page?.totalElements ?? rawPatients.length

  function handleRefresh() {
    void queryClient.invalidateQueries({ queryKey: PATIENTS_QUERY_KEY })
  }

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-border pb-4">
        <div>
          <div className="flex items-center gap-3">
            <h2 className="text-xl font-bold tracking-tight text-text-primary">
              Patient Registry & Cohort Directory
            </h2>
            {!isLoading && !isError && (
              <span className="rounded bg-elevated px-2.5 py-0.5 text-xs font-mono font-semibold text-text-primary border border-border">
                {totalElements} Registered
              </span>
            )}
          </div>
          <p className="mt-1 text-xs text-text-secondary">
            Computational digital twin records, observed telemetry histories, and clinical demographic parameters.
          </p>
        </div>

        <div className="flex items-center gap-2.5">
          <button
            onClick={() => setIsEnrollModalOpen(true)}
            className="btn-primary text-xs"
          >
            + Enroll Real Patient
          </button>
          <button
            onClick={handleRefresh}
            disabled={isFetching}
            className="btn-secondary text-xs"
            aria-label="Refresh patient list"
          >
            <IconRefresh size={14} className={isFetching ? 'animate-spin' : ''} />
            Refresh
          </button>
        </div>
      </div>

      {/* Filter and Search Bar */}
      {!isError && (
        <div className="card p-3.5 flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-3 flex-1 min-w-[280px]">
            {/* Search input */}
            <div className="relative flex-1 min-w-[220px] max-w-md">
              <IconSearch
                size={14}
                className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted"
              />
              <input
                type="search"
                value={filterText}
                onChange={(e) => setFilterText(e.target.value)}
                placeholder="Search by Patient UUID…"
                className="input-field w-full pl-8"
                aria-label="Filter patients by UUID"
              />
            </div>

            {/* Category Filter Pills */}
            <div className="flex items-center gap-1.5 text-xs font-mono">
              <button
                onClick={() => setCategoryFilter('ALL')}
                className={`px-2.5 py-1 rounded transition-colors ${
                  categoryFilter === 'ALL'
                    ? 'bg-text-primary text-black font-semibold'
                    : 'bg-elevated text-text-secondary hover:text-text-primary border border-border'
                }`}
              >
                All ({rawPatients.length})
              </button>
              <button
                onClick={() => setCategoryFilter('ENROLLED')}
                className={`px-2.5 py-1 rounded transition-colors ${
                  categoryFilter === 'ENROLLED'
                    ? 'bg-text-primary text-black font-semibold'
                    : 'bg-elevated text-text-secondary hover:text-text-primary border border-border'
                }`}
              >
                Clinical ({rawPatients.filter((p) => !isSyntheticPatient(p.patientId)).length})
              </button>
              <button
                onClick={() => setCategoryFilter('SYNTHETIC')}
                className={`px-2.5 py-1 rounded transition-colors ${
                  categoryFilter === 'SYNTHETIC'
                    ? 'bg-text-primary text-black font-semibold'
                    : 'bg-elevated text-text-secondary hover:text-text-primary border border-border'
                }`}
              >
                Synthetic ({rawPatients.filter((p) => isSyntheticPatient(p.patientId)).length})
              </button>
            </div>
          </div>

          {/* Sort order toggle */}
          <div className="flex items-center gap-2 text-xs text-text-muted">
            <span>Sort:</span>
            <button
              onClick={() => setSortOrder(sortOrder === 'desc' ? 'asc' : 'desc')}
              className="btn-secondary py-1 px-2.5 text-xs font-mono"
            >
              {sortOrder === 'desc' ? 'Newest' : 'Oldest'}
            </button>
          </div>
        </div>
      )}

      {/* Loading State */}
      {isLoading && <LoadingSkeleton rows={5} height="h-16" />}

      {/* Error State */}
      {isError && (
        <ErrorState
          title="Patient Registry Unavailable"
          message={error instanceof Error ? error.message : 'Unable to query /api/v1/patients'}
          onRetry={() => void refetch()}
        />
      )}

      {/* Empty State */}
      {!isLoading && !isError && rawPatients.length === 0 && (
        <EmptyState
          title="No Registered Patients Found"
          description="Click 'Enroll Real Patient' above to create a new patient and ingest baseline EHR data into PostgreSQL."
          icon={<IconUsers size={24} />}
          action={
            <button onClick={() => setIsEnrollModalOpen(true)} className="btn-primary text-xs">
              Enroll Patient Now
            </button>
          }
        />
      )}

      {/* Table Data View */}
      {!isLoading && !isError && rawPatients.length > 0 && (
        <div className="card overflow-hidden">
          {filtered.length === 0 ? (
            <div className="p-8 text-center text-xs text-text-muted">
              No registered patients match filter <span className="font-mono text-text-primary">"{filterText}"</span>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs" aria-label="Patient Registry Table">
                <thead>
                  <tr className="border-b border-border bg-card text-text-muted font-medium uppercase font-mono tracking-wider text-[11px]">
                    <th className="py-3 px-4">Patient Identifier</th>
                    <th className="py-3 px-4">Cohort Classification</th>
                    <th className="py-3 px-4">Digital Twin State</th>
                    <th className="py-3 px-4">Latest Observed Glucose</th>
                    <th className="py-3 px-4">Enrolled Date</th>
                    <th className="py-3 px-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((patient) => (
                    <PatientTableRow
                      key={patient.patientId}
                      patient={patient}
                      onSelect={(id) => navigate(`/patients/${id}`)}
                    />
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Enroll Real Patient Modal */}
      <EnrollPatientModal
        isOpen={isEnrollModalOpen}
        onClose={() => setIsEnrollModalOpen(false)}
        onSuccess={(newId) => {
          navigate(`/patients/${newId}`)
        }}
      />
    </div>
  )
}
