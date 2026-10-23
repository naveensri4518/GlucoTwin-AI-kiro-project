import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/apiClient'
import { PATIENTS_QUERY_KEY } from '@/hooks/usePatients'
import { IconCheckCircle } from './Icons'

interface EnrollPatientModalProps {
  isOpen: boolean
  onClose: () => void
  onSuccess: (patientId: string) => void
}

function generateUuid(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

export function EnrollPatientModal({ isOpen, onClose, onSuccess }: EnrollPatientModalProps) {
  const queryClient = useQueryClient()

  const [patientId, setPatientId] = useState(generateUuid())
  const [dateOfBirth, setDateOfBirth] = useState('1984-06-15')
  const [sex, setSex] = useState<'MALE' | 'FEMALE' | 'OTHER'>('FEMALE')
  const [bmi, setBmi] = useState('27.2')
  const [diabetesOnsetDate, setDiabetesOnsetDate] = useState('2020-03-10')
  const [hba1c, setHba1c] = useState('7.1')
  const [fastingGlucose, setFastingGlucose] = useState('6.8')

  const [isSubmitting, setIsSubmitting] = useState(false)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)
  const [successId, setSuccessId] = useState<string | null>(null)

  if (!isOpen) return null

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setErrorMsg(null)
    setIsSubmitting(true)

    try {
      const parsedBmi = parseFloat(bmi)
      const parsedHba1c = parseFloat(hba1c)
      const parsedFasting = parseFloat(fastingGlucose)

      if (isNaN(parsedBmi) || parsedBmi <= 10 || parsedBmi >= 80) {
        throw new Error('Please enter a realistic BMI between 10 and 80 kg/m²')
      }
      if (isNaN(parsedHba1c) || parsedHba1c <= 3 || parsedHba1c >= 20) {
        throw new Error('Please enter a valid HbA1c value between 3.0% and 20.0%')
      }
      if (isNaN(parsedFasting) || parsedFasting <= 1 || parsedFasting >= 35) {
        throw new Error('Please enter a valid Fasting Glucose between 1.0 and 35.0 mmol/L')
      }

      const payload = {
        dateOfBirth,
        sex,
        bmi: parsedBmi,
        diabetesOnsetDate,
        hba1c: parsedHba1c,
        fastingGlucose: parsedFasting,
      }

      await api.post(`/api/v1/patients/${patientId}/ehr`, payload)

      // Ingest initial baseline wearable observation so the twin dynamic layer has valid telemetry
      try {
        await api.post(`/api/v1/patients/${patientId}/wearable-events`, {
          glucoseReading: parsedFasting,
          heartRate: 72.0,
          hrv: 45.0,
          sleepDuration: 7.5,
          sleepStage: 'LIGHT',
          stepCount: 2500,
          activityLevel: 'LIGHT',
          eventTimestamp: new Date(Date.now() - 2000).toISOString(),
        })
      } catch {
        // Wearable baseline initialization is non-fatal if streaming pipeline catches it subsequently
      }

      // Refresh patients cache
      await queryClient.invalidateQueries({ queryKey: PATIENTS_QUERY_KEY })

      setSuccessId(patientId)
      setIsSubmitting(false)
      onSuccess(patientId)
    } catch (err: unknown) {
      setIsSubmitting(false)
      if (err instanceof Error) {
        setErrorMsg(err.message)
      } else {
        setErrorMsg('Failed to ingest EHR record. Please check inputs.')
      }
    }
  }

  function handleReset() {
    setPatientId(generateUuid())
    setSuccessId(null)
    setErrorMsg(null)
    onClose()
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 overflow-y-auto"
      role="dialog"
      aria-modal="true"
      aria-labelledby="enroll-modal-title"
    >
      <div className="card-elevated w-full max-w-lg border-border bg-card p-6 shadow-2xl space-y-5">
        {/* Modal Header */}
        <div className="flex items-start justify-between border-b border-border pb-3">
          <div>
            <h3 id="enroll-modal-title" className="text-sm font-semibold text-text-primary">
              Enroll Real Patient & Ingest EHR Record
            </h3>
            <p className="text-[11px] text-text-secondary mt-0.5">
              Initializes an immutable physiological digital twin record in PostgreSQL.
            </p>
          </div>
          <button
            onClick={handleReset}
            className="text-text-muted hover:text-text-primary text-sm p-1"
            aria-label="Close modal"
          >
            ✕
          </button>
        </div>

        {/* Success View */}
        {successId ? (
          <div className="space-y-4 py-4 text-center">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-status-success/15 text-status-success border border-status-success/30">
              <IconCheckCircle size={24} />
            </div>
            <div>
              <h4 className="text-sm font-semibold text-text-primary">Patient Enrolled Successfully</h4>
              <p className="font-mono text-xs text-text-secondary mt-1 select-all">{successId}</p>
              <p className="text-[11px] text-text-muted mt-2">
                Digital twin state created and initialized with static EHR baseline layer.
              </p>
            </div>
            <div className="flex justify-center gap-3 pt-2">
              <button
                onClick={() => {
                  onClose()
                }}
                className="btn-secondary text-xs"
              >
                Close
              </button>
              <button
                onClick={() => {
                  onClose()
                  onSuccess(successId)
                }}
                className="btn-primary text-xs"
              >
                Open Digital Twin Workspace →
              </button>
            </div>
          </div>
        ) : (
          /* Form View */
          <form onSubmit={handleSubmit} className="space-y-4 text-xs">
            {errorMsg && (
              <div className="rounded-lg border border-status-danger/40 bg-status-danger/10 p-3 text-status-danger text-[11px]">
                {errorMsg}
              </div>
            )}

            {/* Patient UUID */}
            <div>
              <div className="flex items-center justify-between mb-1">
                <label className="text-[11px] font-medium text-text-secondary">
                  Patient Identifier (UUID)
                </label>
                <button
                  type="button"
                  onClick={() => setPatientId(generateUuid())}
                  className="text-[10px] text-text-muted hover:text-text-primary underline"
                >
                  Generate New UUID
                </button>
              </div>
              <input
                type="text"
                required
                value={patientId}
                onChange={(e) => setPatientId(e.target.value)}
                className="input-field w-full font-mono text-[11px]"
              />
            </div>

            {/* DOB & Sex */}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-[11px] font-medium text-text-secondary mb-1">
                  Date of Birth
                </label>
                <input
                  type="date"
                  required
                  value={dateOfBirth}
                  onChange={(e) => setDateOfBirth(e.target.value)}
                  className="input-field w-full"
                />
              </div>

              <div>
                <label className="block text-[11px] font-medium text-text-secondary mb-1">
                  Biological Sex
                </label>
                <select
                  value={sex}
                  onChange={(e) => setSex(e.target.value as 'MALE' | 'FEMALE' | 'OTHER')}
                  className="input-field w-full cursor-pointer"
                >
                  <option value="FEMALE">Female</option>
                  <option value="MALE">Male</option>
                  <option value="OTHER">Other</option>
                </select>
              </div>
            </div>

            {/* BMI & Diabetes Onset */}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-[11px] font-medium text-text-secondary mb-1">
                  BMI (kg/m²)
                </label>
                <input
                  type="number"
                  step="0.1"
                  required
                  value={bmi}
                  onChange={(e) => setBmi(e.target.value)}
                  placeholder="e.g. 27.5"
                  className="input-field w-full tabular-nums"
                />
              </div>

              <div>
                <label className="block text-[11px] font-medium text-text-secondary mb-1">
                  Diabetes Diagnosis Onset
                </label>
                <input
                  type="date"
                  required
                  value={diabetesOnsetDate}
                  onChange={(e) => setDiabetesOnsetDate(e.target.value)}
                  className="input-field w-full"
                />
              </div>
            </div>

            {/* HbA1c & Fasting Glucose */}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-[11px] font-medium text-text-secondary mb-1">
                  HbA1c (%)
                </label>
                <input
                  type="number"
                  step="0.1"
                  required
                  value={hba1c}
                  onChange={(e) => setHba1c(e.target.value)}
                  placeholder="e.g. 7.4"
                  className="input-field w-full tabular-nums"
                />
              </div>

              <div>
                <label className="block text-[11px] font-medium text-text-secondary mb-1">
                  Fasting Glucose (mmol/L)
                </label>
                <input
                  type="number"
                  step="0.1"
                  required
                  value={fastingGlucose}
                  onChange={(e) => setFastingGlucose(e.target.value)}
                  placeholder="e.g. 6.8"
                  className="input-field w-full tabular-nums"
                />
              </div>
            </div>

            {/* Provenance Notice */}
            <div className="rounded border border-border bg-bg-elevated/40 p-2.5 text-[10px] text-text-muted leading-relaxed">
              <strong className="text-text-secondary">Data Provenance:</strong> Submitted clinical values will be tagged as <span className="font-mono text-status-info">OBSERVED</span> and persisted directly into PostgreSQL.
            </div>

            {/* Action Buttons */}
            <div className="flex items-center justify-end gap-3 pt-2 border-t border-border">
              <button
                type="button"
                onClick={handleReset}
                disabled={isSubmitting}
                className="btn-secondary text-xs"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={isSubmitting}
                className="btn-primary text-xs"
              >
                {isSubmitting ? 'Ingesting Record…' : 'Enroll & Persist Patient'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}
