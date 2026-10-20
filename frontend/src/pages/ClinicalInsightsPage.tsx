import { useSearchParams } from 'react-router-dom'
import { usePatients } from '@/hooks/usePatients'
import { PatientContextSelector } from '@/components/common/PatientContextSelector'
import { ClinicalInsightPanel } from '@/components/ClinicalInsightPanel'
import { ProvenanceBadge } from '@/components/common/ProvenanceBadge'

export default function ClinicalInsightsPage() {
  const [searchParams] = useSearchParams()
  const { data: patientsData } = usePatients()
  const patients = patientsData?.content ?? []

  const activePatientId =
    searchParams.get('patientId') || patients[0]?.patientId || ''

  return (
    <div className="space-y-6">
      {/* Module Title */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-border pb-4">
        <div>
          <div className="flex items-center gap-2.5">
            <h2 className="text-xl font-bold tracking-tight text-text-primary">
              AI Agent Clinical Insights
            </h2>
            <ProvenanceBadge label="PREDICTED" />
          </div>
          <p className="mt-1 text-xs text-text-secondary">
            Multi-signal clinical evidence synthesis combining Digital Twin physiological telemetry, ML prediction vectors, and clinical reasoning traces.
          </p>
        </div>
      </div>

      {/* Patient Context Switcher */}
      <PatientContextSelector currentPatientId={activePatientId} />

      {/* Dedicated Clinical Insight Panel */}
      {activePatientId ? (
        <ClinicalInsightPanel patientId={activePatientId} />
      ) : (
        <div className="card p-8 text-center text-xs text-text-muted">
          No patients found in registry. Please enroll a patient to generate insights.
        </div>
      )}
    </div>
  )
}
