import { useParams, useNavigate } from 'react-router-dom'
import { usePatients } from '@/hooks/usePatients'
import { SectionCard } from '@/components/common/SectionCard'
import { LoadingSkeleton } from '@/components/common/LoadingSkeleton'
import { EmptyState } from '@/components/common/EmptyState'
import {
  IconBrain,
  IconSparkles,
  IconTwin,
  IconHeartPulse,
  IconSliders,
  IconArrowRight,
} from '@/components/common/Icons'

const SECTION_CONFIG: Record<
  string,
  {
    title: string
    subtitle: string
    description: string
    icon: (props: { size?: number }) => React.ReactElement
    rowAnchor: string
  }
> = {
  predictions: {
    title: '2-Hour Glucose Spike Predictions',
    subtitle: 'Machine learning inference & probability forecasting',
    description:
      'Predictions are evaluated against a continuous digital twin physiological state. Select an enrolled patient to view or trigger prediction models.',
    icon: IconBrain,
    rowAnchor: 'prediction-dashboard',
  },
  insights: {
    title: 'Agent Clinical Insights',
    subtitle: 'Multi-signal clinical evidence and decision traces',
    description:
      'Synthesizes observed vitals, predictions, and clinical rules into a concise medical narrative. Select a patient to view generated insights.',
    icon: IconSparkles,
    rowAnchor: 'clinical-insights',
  },
  twin: {
    title: 'Physiological Digital Twin State',
    subtitle: 'Static EHR attributes & dynamic wearable layers',
    description:
      'Maintains twin versioning, rolling CGM buffers, and metabolic parameters. Select a patient to inspect their active twin state.',
    icon: IconTwin,
    rowAnchor: 'digital-twin-overview',
  },
  wearables: {
    title: 'Wearable Continuous Telemetry',
    subtitle: 'Continuous glucose monitoring, heart rate & activity',
    description:
      'Processes streaming biometric events from Redis streams into physiological metrics. Select a patient to view telemetry logs.',
    icon: IconHeartPulse,
    rowAnchor: 'wearable-monitoring',
  },
  simulations: {
    title: 'What-If Counterfactual Simulations',
    subtitle: 'Hypothetical scenario modeling without altering observed patient state',
    description:
      'Test hypothetical carbohydrate meals and activity modifications against calibrated baseline states. Select a patient to launch a simulation.',
    icon: IconSliders,
    rowAnchor: 'what-if-simulation',
  },
}

export default function ClinicalWorkspacePage() {
  const { section = 'predictions' } = useParams<{ section: string }>()
  const navigate = useNavigate()
  const { data, isLoading } = usePatients()

  const config = SECTION_CONFIG[section] ?? SECTION_CONFIG.predictions
  const Icon = config.icon
  const patients = data?.content ?? []

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3 border-b border-border pb-4">
        <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-accent/15 text-accent border border-accent/30">
          <Icon size={24} />
        </div>
        <div>
          <h2 className="text-xl font-bold tracking-tight text-text-primary">
            {config.title}
          </h2>
          <p className="text-xs text-text-secondary">{config.subtitle}</p>
        </div>
      </div>

      <div className="card p-5 border-status-info/30 bg-status-info/5">
        <p className="text-xs text-text-primary font-medium">Clinical Context Notice</p>
        <p className="mt-1 text-xs text-text-secondary leading-relaxed">
          {config.description}
        </p>
      </div>

      <SectionCard
        title="Select Patient to Launch Workspace"
        subtitle="Choose an enrolled patient to open this module with live clinical telemetry"
      >
        {isLoading ? (
          <LoadingSkeleton rows={3} height="h-14" />
        ) : patients.length === 0 ? (
          <EmptyState
            title="No Patients Enrolled"
            description="No patients found in the registry. Please upload an EHR record to begin."
            action={
              <button onClick={() => navigate('/patients')} className="btn-primary text-xs">
                Go to Registry
              </button>
            }
          />
        ) : (
          <div className="grid gap-3 sm:grid-cols-2">
            {patients.map((p) => (
              <div
                key={p.patientId}
                className="card p-4 hover:border-accent flex items-center justify-between gap-3 transition-colors"
              >
                <div className="min-w-0">
                  <p className="font-mono text-xs font-semibold text-text-primary truncate">
                    {p.patientId}
                  </p>
                  <p className="text-[11px] text-text-muted mt-0.5">
                    Enrolled {new Date(p.createdAt).toLocaleDateString()}
                  </p>
                </div>
                <button
                  onClick={() => navigate(`/patients/${p.patientId}#${config.rowAnchor}`)}
                  className="btn-primary text-xs flex-shrink-0"
                >
                  Select Patient <IconArrowRight size={12} />
                </button>
              </div>
            ))}
          </div>
        )}
      </SectionCard>
    </div>
  )
}
