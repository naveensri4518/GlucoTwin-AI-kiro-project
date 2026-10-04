import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { usePatients, PATIENTS_QUERY_KEY } from '@/hooks/usePatients'
import type { PatientSummary } from '@/types/patient'

// ---- helpers ----------------------------------------------------------------

function formatDate(iso: string): string {
  try {
    return new Intl.DateTimeFormat('en-GB', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      timeZoneName: 'short',
    }).format(new Date(iso))
  } catch {
    return iso
  }
}

/** Truncates a UUID to its first segment for compact display: a1000000-… */
function shortId(uuid: string): string {
  return uuid.split('-')[0] ?? uuid
}

// ---- sub-components ---------------------------------------------------------

function LoadingState() {
  return (
    <div className="flex flex-col gap-3" role="status" aria-label="Loading patients">
      {Array.from({ length: 5 }).map((_, i) => (
        <div
          key={i}
          className="card h-[72px] animate-pulse bg-surface-raised"
          aria-hidden="true"
        />
      ))}
      <span className="sr-only">Loading patient list…</span>
    </div>
  )
}

function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div
      className="card flex flex-col items-center gap-3 p-10 text-center"
      role="alert"
      aria-live="assertive"
    >
      <span className="text-2xl" aria-hidden="true">⚠</span>
      <p className="text-sm font-medium text-slate-300">Failed to load patients</p>
      <p className="text-xs text-slate-500">{message}</p>
      <button
        onClick={onRetry}
        className="mt-2 rounded-md bg-accent px-4 py-1.5 text-xs font-medium text-white hover:bg-accent-hover focus:outline-none focus:ring-2 focus:ring-accent"
      >
        Retry
      </button>
    </div>
  )
}

function EmptyState() {
  return (
    <div className="card flex flex-col items-center gap-3 p-12 text-center">
      <span className="text-3xl" aria-hidden="true">👤</span>
      <p className="text-sm font-medium text-slate-300">No patients registered</p>
      <p className="text-xs text-slate-500">
        Upload EHR data via{' '}
        <code className="rounded bg-surface px-1 py-0.5 font-mono text-[11px] text-accent">
          POST /api/v1/patients/:id/ehr
        </code>{' '}
        to register the first patient.
      </p>
    </div>
  )
}

function PatientCard({
  patient,
  onSelect,
}: {
  patient: PatientSummary
  onSelect: (id: string) => void
}) {
  return (
    <article
      className="card flex items-center justify-between gap-4 px-5 py-4 transition-colors hover:border-accent/40"
    >
      {/* Identity */}
      <div className="flex min-w-0 flex-1 items-center gap-4">
        {/* Avatar placeholder — no name available */}
        <div
          className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full bg-accent/10 text-sm font-semibold text-accent"
          aria-hidden="true"
        >
          {shortId(patient.patientId).slice(0, 2).toUpperCase()}
        </div>

        <div className="min-w-0">
          {/* Show full UUID — do not fabricate a name */}
          <p className="truncate font-mono text-xs font-medium text-slate-100">
            {patient.patientId}
          </p>
          <p className="mt-0.5 text-[11px] text-slate-500">
            Registered:{' '}
            <time dateTime={patient.createdAt}>{formatDate(patient.createdAt)}</time>
          </p>
        </div>
      </div>

      {/* Actions */}
      <button
        onClick={() => onSelect(patient.patientId)}
        className="flex-shrink-0 rounded-md bg-accent/10 px-3 py-1.5 text-xs font-medium text-accent transition-colors hover:bg-accent/20 focus:outline-none focus:ring-2 focus:ring-accent"
        aria-label={`View Digital Twin for patient ${patient.patientId}`}
      >
        View Digital Twin →
      </button>
    </article>
  )
}

// ---- main component ---------------------------------------------------------

export default function PatientsPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [filterText, setFilterText] = useState('')

  const { data, isLoading, isError, error, isFetching, refetch } = usePatients()

  // Local UUID filter — no extra backend endpoint needed
  const patients = data?.content ?? []
  const filtered = filterText.trim()
    ? patients.filter((p) =>
        p.patientId.toLowerCase().includes(filterText.trim().toLowerCase())
      )
    : patients

  const totalElements = data?.page.totalElements ?? 0

  function handleRefresh() {
    void queryClient.invalidateQueries({ queryKey: PATIENTS_QUERY_KEY })
  }

  return (
    <div>
      {/* Page header */}
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold text-slate-100">Patients</h2>
          <p className="mt-0.5 text-xs text-slate-400">
            Digital Twin workspace — select a patient to view their glucose predictions.
          </p>
        </div>

        <div className="flex items-center gap-2">
          {/* Patient count badge */}
          {!isLoading && !isError && (
            <span className="rounded-full bg-surface-raised border border-surface-border px-2.5 py-0.5 text-xs text-slate-400">
              {totalElements} patient{totalElements !== 1 ? 's' : ''}
            </span>
          )}

          {/* Refresh */}
          <button
            onClick={handleRefresh}
            disabled={isFetching}
            className="flex items-center gap-1.5 rounded-md border border-surface-border bg-surface-raised px-3 py-1.5 text-xs text-slate-400 transition-colors hover:text-slate-100 disabled:opacity-50 focus:outline-none focus:ring-2 focus:ring-accent"
            aria-label="Refresh patient list"
          >
            <span
              className={isFetching ? 'animate-spin' : ''}
              aria-hidden="true"
            >
              ↻
            </span>
            Refresh
          </button>
        </div>
      </div>

      {/* Local search — filters by UUID fragment, no new API call */}
      {!isError && (
        <div className="mb-4">
          <label htmlFor="patient-search" className="sr-only">
            Filter patients by ID
          </label>
          <input
            id="patient-search"
            type="search"
            value={filterText}
            onChange={(e) => setFilterText(e.target.value)}
            placeholder="Filter by patient ID…"
            className="w-full rounded-md border border-surface-border bg-surface-raised px-3 py-2 text-xs text-slate-100 placeholder-slate-500 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent sm:w-72"
          />
          {filterText && (
            <p className="mt-1 text-[11px] text-slate-500">
              {filtered.length} of {patients.length} patients match
            </p>
          )}
        </div>
      )}

      {/* States */}
      {isLoading && <LoadingState />}

      {isError && (
        <ErrorState
          message={
            error instanceof Error ? error.message : 'An unexpected error occurred'
          }
          onRetry={() => void refetch()}
        />
      )}

      {!isLoading && !isError && patients.length === 0 && <EmptyState />}

      {/* Patient list */}
      {!isLoading && !isError && patients.length > 0 && (
        <>
          {filtered.length === 0 ? (
            <p className="text-center text-xs text-slate-500 py-8">
              No patients match "{filterText}"
            </p>
          ) : (
            <ul className="flex flex-col gap-2" role="list" aria-label="Patient list">
              {filtered.map((patient) => (
                <li key={patient.patientId}>
                  <PatientCard
                    patient={patient}
                    onSelect={(id) => navigate(`/patients/${id}`)}
                  />
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </div>
  )
}
