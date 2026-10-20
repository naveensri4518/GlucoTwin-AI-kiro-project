import { SectionCard } from '@/components/common/SectionCard'
import { StatusBadge } from '@/components/common/StatusBadge'

export default function SettingsPage() {
  return (
    <div className="space-y-6">
      <div className="border-b border-border pb-4">
        <h2 className="text-xl font-bold tracking-tight text-text-primary">
          Platform Configuration & Credentials
        </h2>
        <p className="mt-1 text-xs text-text-secondary">
          Microservice endpoint routing, model version locks, and role access controls.
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <SectionCard
          title="Machine Learning Parameters"
          subtitle="Model hyperparameters & inference controls"
        >
          <div className="space-y-3 text-xs">
            <div className="flex items-center justify-between p-3 rounded-lg bg-bg-card border border-border">
              <div>
                <p className="font-semibold text-text-primary">Inference Engine</p>
                <p className="text-[11px] text-text-muted">Algorithm & framework</p>
              </div>
              <span className="font-mono text-xs text-accent">XGBoost v1.0.0 (Scikit-Learn)</span>
            </div>

            <div className="flex items-center justify-between p-3 rounded-lg bg-bg-card border border-border">
              <div>
                <p className="font-semibold text-text-primary">Spike Horizon Window</p>
                <p className="text-[11px] text-text-muted">Forward prediction duration</p>
              </div>
              <span className="font-mono text-xs text-text-primary">2.0 Hours (120 mins)</span>
            </div>

            <div className="flex items-center justify-between p-3 rounded-lg bg-bg-card border border-border">
              <div>
                <p className="font-semibold text-text-primary">Spike Threshold</p>
                <p className="text-[11px] text-text-muted">Postprandial glucose threshold</p>
              </div>
              <span className="font-mono text-xs text-status-warning font-semibold">
                ≥ 10.0 mmol/L (180 mg/dL)
              </span>
            </div>

            <div className="flex items-center justify-between p-3 rounded-lg bg-bg-card border border-border">
              <div>
                <p className="font-semibold text-text-primary">SHAP Explanations</p>
                <p className="text-[11px] text-text-muted">Feature importance mode</p>
              </div>
              <StatusBadge status="ACTIVE" />
            </div>
          </div>
        </SectionCard>

        <SectionCard
          title="Clinical Environment Credentials"
          subtitle="Authentication parameters & API gateway"
        >
          <div className="space-y-3 text-xs">
            <div className="flex items-center justify-between p-3 rounded-lg bg-bg-card border border-border">
              <div>
                <p className="font-semibold text-text-primary">Current Session Role</p>
                <p className="text-[11px] text-text-muted">Header: X-Dev-Auth</p>
              </div>
              <span className="rounded bg-accent/15 px-2 py-0.5 font-mono text-xs text-accent border border-accent/30 font-semibold">
                ROLE_CLINICIAN
              </span>
            </div>

            <div className="flex items-center justify-between p-3 rounded-lg bg-bg-card border border-border">
              <div>
                <p className="font-semibold text-text-primary">Gateway Base URL</p>
                <p className="text-[11px] text-text-muted">Vite proxy configuration</p>
              </div>
              <span className="font-mono text-xs text-text-secondary">http://localhost:8080/api</span>
            </div>

            <div className="flex items-center justify-between p-3 rounded-lg bg-bg-card border border-border">
              <div>
                <p className="font-semibold text-text-primary">LLM Reasoning Provider</p>
                <p className="text-[11px] text-text-muted">Deterministic Fallback Engine</p>
              </div>
              <span className="rounded bg-status-success/15 px-2 py-0.5 font-mono text-xs text-status-success border border-status-success/30 font-semibold">
                RULE-BASED ACTIVE
              </span>
            </div>
          </div>
        </SectionCard>
      </div>
    </div>
  )
}
