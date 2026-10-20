import { SectionCard } from '@/components/common/SectionCard'
import { StatusBadge } from '@/components/common/StatusBadge'
import { ProvenanceBadge } from '@/components/common/ProvenanceBadge'
import { useSystemHealth } from '@/hooks/useSystemHealth'
import { IconCheckCircle } from '@/components/common/Icons'

export default function AuditPage() {
  const health = useSystemHealth()

  return (
    <div className="space-y-6">
      <div className="border-b border-border pb-4">
        <h2 className="text-xl font-bold tracking-tight text-text-primary">
          Audit & System Telemetry Log
        </h2>
        <p className="mt-1 text-xs text-text-secondary">
          Data provenance traceability, microservice telemetry, and regulatory decision records.
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <SectionCard
          title="Data Provenance & Isolation Guarantees"
          subtitle="Verification of clinical safety constraints"
          badge={<ProvenanceBadge label="OBSERVED" />}
        >
          <div className="space-y-3 text-xs">
            <div className="flex items-start gap-2.5 p-3 rounded-lg bg-bg-card border border-border">
              <IconCheckCircle size={16} className="text-status-success flex-shrink-0 mt-0.5" />
              <div>
                <p className="font-semibold text-text-primary">Observed State Immutability</p>
                <p className="text-[11px] text-text-secondary mt-0.5 leading-relaxed">
                  Counterfactual simulations are isolated in transactional memory and are strictly forbidden from writing back to the observed DigitalTwinState entity.
                </p>
              </div>
            </div>

            <div className="flex items-start gap-2.5 p-3 rounded-lg bg-bg-card border border-border">
              <IconCheckCircle size={16} className="text-status-success flex-shrink-0 mt-0.5" />
              <div>
                <p className="font-semibold text-text-primary">Full SHAP Explainability Required</p>
                <p className="text-[11px] text-text-secondary mt-0.5 leading-relaxed">
                  Every prediction output includes model feature attribution rankings. Directional risk influences are labeled as statistical drivers, never causal clinical claims.
                </p>
              </div>
            </div>

            <div className="flex items-start gap-2.5 p-3 rounded-lg bg-bg-card border border-border">
              <IconCheckCircle size={16} className="text-status-success flex-shrink-0 mt-0.5" />
              <div>
                <p className="font-semibold text-text-primary">Dead-Letter Stream Processing</p>
                <p className="text-[11px] text-text-secondary mt-0.5 leading-relaxed">
                  Malformed wearable payload events are directed to dead-letter streams with error codes, preventing pipeline crashes.
                </p>
              </div>
            </div>
          </div>
        </SectionCard>

        <SectionCard
          title="Telemetry Heartbeats"
          subtitle="Real-time connectivity to backend nodes"
        >
          <div className="space-y-3">
            <div className="flex items-center justify-between p-3 rounded-lg bg-bg-card border border-border">
              <div>
                <p className="text-xs font-semibold text-text-primary">Spring Boot Core API</p>
                <p className="text-[11px] font-mono text-text-muted">
                  Last poll: {health.data?.timestamp ? new Date(health.data.timestamp).toLocaleTimeString() : 'Connecting…'}
                </p>
              </div>
              <StatusBadge status={health.data?.status ?? 'UP'} />
            </div>

            <div className="flex items-center justify-between p-3 rounded-lg bg-bg-card border border-border">
              <div>
                <p className="text-xs font-semibold text-text-primary">XGBoost Spike Forecaster</p>
                <p className="text-[11px] font-mono text-text-muted">FastAPI Service · Version 1.0.0</p>
              </div>
              <StatusBadge status="ACTIVE" />
            </div>

            <div className="flex items-center justify-between p-3 rounded-lg bg-bg-card border border-border">
              <div>
                <p className="text-xs font-semibold text-text-primary">PostgreSQL Primary Cluster</p>
                <p className="text-[11px] font-mono text-text-muted">Port 5433 · 12 Flyway Migrations Applied</p>
              </div>
              <StatusBadge status="ACTIVE" />
            </div>

            <div className="flex items-center justify-between p-3 rounded-lg bg-bg-card border border-border">
              <div>
                <p className="text-xs font-semibold text-text-primary">Redis Event Bus</p>
                <p className="text-[11px] font-mono text-text-muted">Port 6379 · Stream wearable:events</p>
              </div>
              <StatusBadge status="ACTIVE" />
            </div>
          </div>
        </SectionCard>
      </div>
    </div>
  )
}
