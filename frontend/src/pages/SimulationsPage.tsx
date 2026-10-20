import { useSearchParams } from 'react-router-dom'
import { usePatients } from '@/hooks/usePatients'
import { PatientContextSelector } from '@/components/common/PatientContextSelector'
import { WhatIfSimulationPanel } from '@/components/WhatIfSimulationPanel'
import { ProvenanceBadge } from '@/components/common/ProvenanceBadge'

export default function SimulationsPage() {
  const [searchParams] = useSearchParams()
  const { data: patientsData } = usePatients()
  const patients = patientsData?.content ?? []

  const activePatientId =
    searchParams.get('patientId') || patients[0]?.patientId || ''

  return (
    <div className="space-y-6">
      {/* Title */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-border pb-4">
        <div>
          <div className="flex items-center gap-2.5">
            <h2 className="text-xl font-bold tracking-tight text-text-primary">
              What-If Counterfactual Simulations
            </h2>
            <ProvenanceBadge label="SIMULATED" />
          </div>
          <p className="mt-1 text-xs text-text-secondary">
            Test counterfactual behavioral and nutritional modifications against calibrated baseline states without altering the observed patient record.
          </p>
        </div>
      </div>

      {/* Patient Switcher */}
      <PatientContextSelector currentPatientId={activePatientId} />

      {/* Dedicated Simulation Panel */}
      {activePatientId ? (
        <WhatIfSimulationPanel patientId={activePatientId} />
      ) : (
        <div className="card p-8 text-center text-xs text-text-muted">
          No patients found in registry. Please enroll a patient to run counterfactual simulations.
        </div>
      )}
    </div>
  )
}
